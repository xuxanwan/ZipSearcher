package com.github.venkataraju.zipsearch;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Formatter;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import com.github.venkataraju.zipsearch.MainUI.GbLayoutHelper.Fill;

@SuppressWarnings("serial")
public final class MainUI extends JFrame
{
	private static final int[] TIMES_IN_MILLIS = { 36_00_000, 60_000, 1000 };
	private static final char[] TIME_UNIT_SYMBOLS = { 'h', 'm', 's' };

	private static final String FILE_PATHS_TOOLTIP_TEXT = "Enter comma(,) or path separator(" + File.pathSeparator
			+ ") separated directory/zip compatible file paths";

	private final SimpleAttributeSet infTextStyle = new SimpleAttributeSet(),
			errorTextStyle = new SimpleAttributeSet(),
			matchTextStyle = new SimpleAttributeSet();
	private final Border errorBorder = BorderFactory.createLineBorder(new Color(220, 0, 0));

	private final JTextField searchPathsTf = new JTextField();
	private final JButton browseBtn;

	private final JTextField typeOfFilesTf = new JTextField();
	private final JCheckBox searchWithInArchivesCb = new JCheckBox("Archives within archives"),
			caseInsensitiveSearchCb = new JCheckBox("Case sensitive search", true);

	private final JTextField searchFileTf = new JTextField();
	private final JButton searchBtn = new JButton("Search"),
			stopBtn = new JButton("Stop");

	// Note: 1 space string passed to make it visible in the UI (though there is
	// no data)
	private final JLabel msgLbl = new JLabel(), statusLbl = new JLabel(" ");;
	private final Border defaultTextFieldBorder;

	private final StyledDocument results;

	private final Color msgLblDefaultForeground;

	private ZipSearcher zipSearcher;

	private ActionListener timerListener;
	private Timer timer;

	private long startTime;
	private int noOfResults;

	private MainUI()
	{
		super("ZipSearcher");

		Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 15);

		// BufferedImage bufferedImage = new BufferedImage(100, 100,
		// BufferedImage.TYPE_INT_ARGB);
		// Graphics2D g2d = bufferedImage.createGraphics();
		// g2d.setColor(new Color(255, 0, 0, 50));
		// g2d.fillRect(0, 0, 100, 100);
		// g2d.setColor(new Color(255, 0, 0, 200));
		// g2d.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 70));
		// g2d.drawString("AS", 5, 80);
		// setIconImage(bufferedImage);

		StyleConstants.setForeground(infTextStyle, Color.BLACK);
		StyleConstants.setForeground(errorTextStyle, Color.RED);
		StyleConstants.setBold(matchTextStyle, true);

		UIManager.put("ToolTip.background", Color.WHITE);
		UIManager.put("ToolTip.foreground", Color.BLACK);
		UIManager.put("ToolTip.font", font);
		UIManager.put("ToolTip.border", new OvalBorder(4, Color.BLACK));

		Container cp = getContentPane();

		JPanel controlsPanel = new JPanel();
		cp.add(controlsPanel, BorderLayout.NORTH);

		GbLayoutHelper gbLayoutHelper = new GbLayoutHelper(controlsPanel);

		JLabel searchPathsLbl = new JLabel("Search in: ");
		searchPathsLbl.setLabelFor(searchPathsTf);
		searchPathsLbl.setDisplayedMnemonic('e');

		defaultTextFieldBorder = searchPathsTf.getBorder();

		searchPathsTf.setName("searchPathsTf");
		searchPathsTf.setToolTipText(FILE_PATHS_TOOLTIP_TEXT);
		searchPathsTf.getDocument().addDocumentListener(new DocumentListener()
		{
			public void changedUpdate(DocumentEvent e)
			{
				updatePathToolTip();
			}

			public void insertUpdate(DocumentEvent e)
			{
				updatePathToolTip();
			}

			public void removeUpdate(DocumentEvent e)
			{
				updatePathToolTip();
			}

			private void updatePathToolTip()
			{
				List<String> fileNames = split(searchPathsTf.getText(), ",");
				if (fileNames.isEmpty())
				{
					searchPathsTf.setToolTipText(FILE_PATHS_TOOLTIP_TEXT);
					return;
				}

				StringBuilder tooltip = new StringBuilder(300).append("<html>");
				{
					int currentLineLen = 0, maxStrLenPerLine = 150;
					int currentLineNo = 1, maxNoOfLines = 5;
					int noOfFileNames = 0;
					for (String fileName : fileNames)
					{
						if (currentLineLen != 0 && (currentLineLen + fileName.length()) > maxStrLenPerLine)
						{
							tooltip.append("<br>");
							currentLineLen = 0;
							currentLineNo++;
						}

						if (currentLineLen != 0)
						{
							tooltip.append(", ");
							currentLineLen += 2;
						}
						tooltip.append(fileName);
						currentLineLen += fileName.length();
						noOfFileNames++;

						if (currentLineNo == maxNoOfLines)
						{
							if (noOfFileNames != fileNames.size())
							{ // Not bothered about maxStrLenPerLine here
								tooltip.append(", ...");
							}
							break;
						}
					}
				}

				tooltip.append("</html>");
				searchPathsTf.setToolTipText(tooltip.toString());
			}
		});

		browseBtn = new JButton("Browse");
		browseBtn.setMnemonic('B');
		browseBtn.setName("browseBtn");
		browseBtn.setToolTipText("Browse files and folders");
		Consumer<ActionEvent> actionConsumer = e ->
		{
			JFileChooser jFileChooser = new JFileChooser();
			jFileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
			jFileChooser.setMultiSelectionEnabled(true);
			jFileChooser.addChoosableFileFilter(new FileFilter()
			{
				@Override
				public String getDescription()
				{
					return "Directories and ZIP compatible files";
				}

				@Override
				public boolean accept(File f)
				{
					return true;
				}
			});
			jFileChooser.setAcceptAllFileFilterUsed(true);

			if (jFileChooser.showDialog(cp, "Select") == JFileChooser.APPROVE_OPTION)
			{
				File[] selectedFiles = jFileChooser.getSelectedFiles();
				String pathStr = Stream.of(selectedFiles).map(File::getPath).collect(Collectors.joining(", "));
				String existingText = searchPathsTf.getText();
				searchPathsTf.setText((existingText.isEmpty() ? "" : (existingText + ", ")) + pathStr);
			}
		};

		addKeyBoardAndMouseAction(browseBtn, "browseFileSystem", KeyEvent.VK_ENTER, actionConsumer);

		JLabel typesOfFilesLbl = new JLabel("Type of files: ");
		typesOfFilesLbl.setLabelFor(typeOfFilesTf);
		typesOfFilesLbl.setDisplayedMnemonic('T');

		typeOfFilesTf.setName("typeOfFilesTf");
		typeOfFilesTf.setToolTipText("File extensions. e.g. jar, war, zip");

		searchWithInArchivesCb.setMnemonic(KeyEvent.VK_A);
		searchWithInArchivesCb.setName("searchWithInArchivesCb");
		searchWithInArchivesCb.setToolTipText("<html>Search in archives within archives<br/>e.g. zip inside another zip</html>");

		JLabel searchFileLbl = new JLabel("For file: ");
		searchFileLbl.setLabelFor(searchFileTf);
		searchFileLbl.setDisplayedMnemonic('F');

		searchFileTf.setName("searchFileTf");
		searchFileTf.setToolTipText("<html>File name to search.<br />Path can be separated by ., / or \\</html>");

		shiftFocusOnUpDownArrowKeys(searchPathsTf, typeOfFilesTf, searchFileTf);

		caseInsensitiveSearchCb.setName("caseInsensitiveSearchCb");
		caseInsensitiveSearchCb.setMnemonic(KeyEvent.VK_I);

		shiftFocusOnUpDownArrowKeys(browseBtn, searchWithInArchivesCb, caseInsensitiveSearchCb);

		JPanel buttonsAndLabelPanel = new JPanel();

		GbLayoutHelper buttonsGbLayoutHelper = new GbLayoutHelper(buttonsAndLabelPanel, new Insets(0, 0, 0, 0));

		/********* These need initialization here as we are using in below runnable ********/

		JTextPane jtp = new JTextPane();
		jtp.setEditable(false);
		results = jtp.getStyledDocument();

		msgLblDefaultForeground = msgLbl.getForeground();

		/**********************************/

		searchBtn.setMnemonic('S');
		TextAreaUpdateHelper textAreaUpdateHelper = new TextAreaUpdateHelper(results);
		actionConsumer = e ->
		{
			searchFileTf.setBorder(defaultTextFieldBorder);
			msgLbl.setText(null);
			msgLbl.setForeground(Color.RED);

			textAreaUpdateHelper.clear();

			if ("Author".equals(searchFileTf.getText()))
			{
				SimpleAttributeSet italicTextStyle = new SimpleAttributeSet();
				StyleConstants.setItalic(italicTextStyle, true);
				textAreaUpdateHelper.addNewLine("S Venkata Raju. raju7003@gmail.com", italicTextStyle);
			}

			List<String> searchPaths = split(searchPathsTf.getText(), ",");
			if (searchPaths.isEmpty())
			{
				msgLbl.setText("Please provide the file/folder path(s)");
				searchPathsTf.requestFocusInWindow();
				searchPathsTf.setBorder(errorBorder);
				return;
			}
			searchPathsTf.setBorder(defaultTextFieldBorder);

			List<String> searchFileExtns = split(typeOfFilesTf.getText(), ",");
			if (searchFileExtns.isEmpty())
			{
				msgLbl.setText("Please provide the type of files to search");
				typeOfFilesTf.requestFocusInWindow();
				typeOfFilesTf.setBorder(errorBorder);
				return;
			}
			typeOfFilesTf.setBorder(defaultTextFieldBorder);

			String filePathToSearch = searchFileTf.getText().trim();

			if (filePathToSearch.isEmpty())
			{
				msgLbl.setText("Please provide the file name");
				searchFileTf.requestFocusInWindow();
				searchFileTf.setBorder(errorBorder);
				return;
			}
			searchFileTf.setBorder(defaultTextFieldBorder);

			searchBtn.setEnabled(false);

			msgLbl.setForeground(msgLblDefaultForeground);

			noOfResults = 0;

			zipSearcher = new ZipSearcher(searchPaths, searchFileExtns,
					searchWithInArchivesCb.isSelected(), filePathToSearch, caseInsensitiveSearchCb.isSelected());
			FutureTask<Void> futureTask = new FutureTask<>(zipSearcher::startSearch, null);
			startTime = System.currentTimeMillis();

			msgLbl.setText(null);

			timerListener = new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					boolean done = futureTask.isDone();

					statusLbl.setText("Searching "
							+ get3DotsString(zipSearcher.getCurrentSearchingFile().toString(), 120));

					int noOfFilesSearched = zipSearcher.getNoOfFilesSearched();
					Collection<Result> newResults = zipSearcher.getNewResults();
					int noOfArchivesSearched = zipSearcher.getNoOfArchivesSearched();
					for (Result result : newResults)
					{
						if (result.resultType == Result.ResultType.MSG)
						{
							noOfResults++;
							// TODO: Hilight
							textAreaUpdateHelper.addNewLine(result.msg, infTextStyle);
						}
						else
						{
							textAreaUpdateHelper.addNewLine(result.msg, errorTextStyle);
						}
					}

					String fmtStr = "<html>Searched <font face='courier'>%s</font> %s, "
							+ "<font face='courier'>%s</font> %s. "
							+ "<font face='courier'>%s</font> %s found. "
							+ "Elapsed time: %s</html>";

					String progress = String.format(fmtStr,
							formatNumber(noOfArchivesSearched),
							properSingularPlural(noOfArchivesSearched, "archive", "archives"),
							formatNumber(noOfFilesSearched),
							properSingularPlural(noOfFilesSearched, "file", "files"),
							formatNumber(noOfResults),
							properSingularPlural(noOfResults, "result", "results"),
							elapsedTime(startTime))
							.toString();
					msgLbl.setText(progress);

					if (done)
					{
						timer.stop();
						statusLbl.setText("Completed");
						stopBtn.setEnabled(false);
						searchBtn.setEnabled(true);
						getRootPane().setDefaultButton(searchBtn);
					}
				}
			};
			timer = new Timer(350, timerListener);
			timer.setInitialDelay(500);
			timer.start();

			getRootPane().setDefaultButton(stopBtn);
			stopBtn.setEnabled(true);

			statusLbl.setText("Searching...");

			Thread thread = new Thread(futureTask);
			thread.setPriority(Thread.NORM_PRIORITY); // Lesser than Swing EDT
			thread.start();
		};
		searchBtn.addActionListener(createAction(actionConsumer));
		JPanel buttonsPanel = new JPanel(new GridLayout(1, 0, 6, 0));

		stopBtn.setMnemonic('p');
		stopBtn.setToolTipText("Stop searching");
		addKeyBoardAndMouseAction(stopBtn, "stop", KeyEvent.VK_ENTER, e ->
		{
			zipSearcher.cancelSearch();
			timer.stop();
			statusLbl.setText("Stopped");
			stopBtn.setEnabled(false);
			searchBtn.setEnabled(true);
			getRootPane().setDefaultButton(searchBtn);
		});
		stopBtn.setEnabled(false);

		msgLbl.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));

		{
			JPanel noWrapPanel = new JPanel(new BorderLayout());
			noWrapPanel.add(jtp);
			JScrollPane jsp = new JScrollPane(noWrapPanel);
			jsp.setViewportView(jtp);
			cp.add(jsp, BorderLayout.CENTER);
		}

		gbLayoutHelper.addComponant(searchPathsLbl, 0, 0, 1, 0.0D);
		gbLayoutHelper.addComponant(searchPathsTf, 1, 0, 1, 1.0D);
		gbLayoutHelper.addComponant(browseBtn, 2, 0, 1, 0.0D, Fill.NONE);
		gbLayoutHelper.addComponant(typesOfFilesLbl, 0, 1, 1, 0.0D);
		gbLayoutHelper.addComponant(typeOfFilesTf, 1, 1, 1, 1.0D);
		gbLayoutHelper.addComponant(searchWithInArchivesCb, 2, 1, 1, 0.0D);
		gbLayoutHelper.addComponant(searchFileLbl, 0, 2, 1, 0.0D);
		gbLayoutHelper.addComponant(searchFileTf, 1, 2, 1, 1.0D);
		gbLayoutHelper.addComponant(caseInsensitiveSearchCb, 2, 2, 1, 0.0D);

		gbLayoutHelper.addComponant(buttonsAndLabelPanel, 0, 3, 3, 1.0D);
		buttonsGbLayoutHelper.addComponant(buttonsPanel, 0, 0, 1, 0.0D);
		buttonsPanel.add(searchBtn);
		buttonsPanel.add(stopBtn);
		buttonsGbLayoutHelper.addComponant(msgLbl, 1, 0, 1, 1.0D);

		getRootPane().setDefaultButton(searchBtn);

		cp.add(statusLbl, BorderLayout.SOUTH);

		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		double width = Math.min(screenSize.getWidth(), 1000);
		double height = width * 3.0D / 5.0D;
		screenSize.setSize(width, height);
		setSize(screenSize);
		setLocationRelativeTo(null);
		setUiFont(cp, font);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		// FIXME: Pressing Escape on file browser closing the main window
		// JRootPane rootPane = getRootPane();
		// addKeyBoardAndMouseAction(rootPane, "exitOnEscapeAction",
		// KeyEvent.VK_ESCAPE, JComponent.WHEN_IN_FOCUSED_WINDOW,
		// e -> System.exit(0));

		setVisible(true);
	}

	/** Warning: Each component should have an unique name */
	private void shiftFocusOnUpDownArrowKeys(JComponent... jcomponents)
	{
		for (int i = 0; i < jcomponents.length; i++)
		{
			JComponent current = jcomponents[i], next = jcomponents[(i + 1) % jcomponents.length];
			addKeyBoardAndMouseAction(current, "ShiftFocusFrom" + current.getName() + "To" + next.getName(),
					KeyEvent.VK_DOWN, e -> next.requestFocusInWindow());
			addKeyBoardAndMouseAction(next, "ShiftFocusFrom" + next.getName() + "To" + current.getName(),
					KeyEvent.VK_UP, e -> current.requestFocusInWindow());
		}
	}

	/** All the parts will be trimmed and empty strings will be ignored */
	private static List<String> split(String str, String regex)
	{
		String[] partsArr = str.split(regex);
		List<String> parts = new ArrayList<String>(partsArr.length);
		for (String part : partsArr)
			if (!(part = part.trim()).isEmpty())
				parts.add(part);
		return parts;
	}

	/*
	 * *********************************************************
	 * Static Utility Methods
	 * *********************************************************
	 */

	private static String elapsedTime(long startTime)
	{
		long elapsedTime = System.currentTimeMillis() - startTime;
		@SuppressWarnings("resource")
		Formatter frmtr = new Formatter();

		boolean added = false;

		for (int i = 0; i < TIMES_IN_MILLIS.length; i++)
		{
			int unitTimeInMillis = TIMES_IN_MILLIS[i];
			int noOfUnits = (int) (elapsedTime / unitTimeInMillis);
			if (added || (noOfUnits > 0) || ((i + 1) == TIMES_IN_MILLIS.length))
			{
				frmtr.format("%2d%c", noOfUnits, TIME_UNIT_SYMBOLS[i]);
				if ((i + 1) < TIMES_IN_MILLIS.length)
					frmtr.format(":");
				elapsedTime %= unitTimeInMillis;
				added = true;
			}
		}

		return frmtr.toString();
	}

	private static void setUiFont(Container container, Font font)
	{
		for (Component component : container.getComponents())
		{
			component.setFont(font);
			if (component instanceof Container)
				setUiFont((Container) component, font);
		}
	}

	private static Action createAction(Consumer<ActionEvent> actionConsumer)
	{
		return new AbstractAction()
		{
			private static final long serialVersionUID = 1L;

			public void actionPerformed(ActionEvent e)
			{
				actionConsumer.accept(e);
			}
		};
	}

	/**
	 * @param keyCode
	 *          e.g. {@link KeyEvent#VK_ENTER}
	 */
	private static void addKeyBoardAndMouseAction(JComponent jc, String actionName,
			int keyCode, Consumer<ActionEvent> consumer)
	{
		addKeyBoardAndMouseAction(jc, actionName, keyCode, JComponent.WHEN_FOCUSED, consumer);
	}

	/**
	 * @param keyCode
	 *          e.g. {@link KeyEvent#VK_ENTER}
	 * @param condition
	 *          One of {@link JComponent#WHEN_IN_FOCUSED_WINDOW},
	 *          {@link JComponent#WHEN_FOCUSED},
	 *          {@link JComponent#WHEN_ANCESTOR_OF_FOCUSED_COMPONENT}
	 */
	private static void addKeyBoardAndMouseAction(JComponent jc, String actionName,
			int keyCode, int condition, Consumer<ActionEvent> consumer)
	{
		KeyStroke keyStroke = KeyStroke.getKeyStroke(keyCode, 0 /* No modifiers */, true /* onKeyRelease */);
		jc.getInputMap(condition).put(keyStroke, actionName);
		Action action = createAction(consumer);
		jc.getActionMap().put(actionName, action);
		if (jc instanceof AbstractButton)
			((AbstractButton) jc).addActionListener(action);
	}

	public static final class GbLayoutHelper
	{
		enum Fill
		{
			NONE,
			HORIZONTAL
		}

		private final Container container;
		private final GridBagLayout gbl;
		private final GridBagConstraints gbc;

		GbLayoutHelper(Container container)
		{
			this(container, new Insets(1, 2, 1, 2));
		}

		GbLayoutHelper(Container container, Insets insets)
		{
			this.container = container;
			this.gbl = new GridBagLayout();
			this.gbc = new GridBagConstraints();
			this.gbc.insets = insets;
			this.container.setLayout(gbl);
		}

		void addComponant(JComponent component, int gridX, int gridY, int gridWidth, double weightX)
		{
			addComponant(component, gridX, gridY, gridWidth, weightX, Fill.HORIZONTAL);
		}

		void addComponant(JComponent component, int gridX, int gridY, int gridWidth, double weightX, Fill fill)
		{
			gbc.gridx = gridX;
			gbc.gridy = gridY;

			gbc.gridwidth = gridWidth;
			gbc.gridheight = 1;

			gbc.fill = (fill == Fill.NONE) ? GridBagConstraints.NONE : GridBagConstraints.HORIZONTAL;

			gbc.weightx = weightX;
			gbc.weighty = 0.0D;

			gbl.setConstraints(component, gbc);

			container.add(component);
		}
	}

	private static class TextAreaUpdateHelper
	{
		private final StyledDocument sd;

		TextAreaUpdateHelper(StyledDocument sd)
		{
			this.sd = sd;
		}

		void clear()
		{
			try
			{
				sd.remove(0, sd.getLength());
			}
			catch (BadLocationException e)
			{
				throw new RuntimeException(e.getMessage(), e);
			}
		}

		void appendText(String txt, AttributeSet as)
		{
			try
			{
				sd.insertString(sd.getLength(), txt, as);
			}
			catch (BadLocationException e)
			{
				throw new RuntimeException(e.getMessage(), e);
			}
		}

		void addNewLine(String txt, AttributeSet as)
		{
			appendText(txt, as);
			appendText(System.lineSeparator(), null);
		}
	}

	private static String get3DotsString(String str, int maxLength)
	{
		if (str.length() <= maxLength)
			return String.format("%-" + maxLength + "s", str).toString();
		if (maxLength < 4)
			return str.substring(0, maxLength);
		int halfWay = maxLength / 2;
		return str.substring(0, halfWay - 1) + "..." + str.substring(str.length() - (maxLength - (halfWay + 2)), str.length());
	}

	/** Formats in 9,99,99,999 style */
	private static String formatNumber(int number)
	{
		String numStr = Integer.toString(number);
		int numStrLen = numStr.length();
		StringBuilder sb = new StringBuilder(numStrLen + ((numStrLen - 2) / 2));
		for (int i = 0, len = numStrLen - 3, j = numStrLen % 2; i < len; i++, j++)
		{
			sb.append(numStr.charAt(i));
			if (j % 2 == 0)
				sb.append(',');
		}
		sb.append(numStr, Math.max(0, numStrLen - 3), numStrLen);
		return sb.toString();
	}

	private static String properSingularPlural(int number, String singular, String plural)
	{
		return (number == 1) ? singular : plural;
	}

	public static void main(String[] args)
	{
		SwingUtilities.invokeLater(MainUI::new);
	}
}
