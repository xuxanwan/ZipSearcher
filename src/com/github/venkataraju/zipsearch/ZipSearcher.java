package com.github.venkataraju.zipsearch;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

final class ZipSearcher
{
	private final Collection<String> searchPaths;
	private final SwingAndNioFilePathFilter swingAndNioFilePathFilter;
	private final boolean searchWithInArchives;
	private final String filePathWithoutExtnToSearch;
	private final int filePathWithoutExtnLen, filePathWithoutExtnLenPlusOne;
	private final boolean caseSensitiveSearch;

	// May not be the file extension, just because it is after last dot(.).
	// User may be searching for package org.xyz
	private final Optional<String> mayBeFileExtn;

	private final Queue<Result> results = new ConcurrentLinkedQueue<>();

	private boolean usedUp;

	private volatile boolean cancelled;
	private volatile Path currentSearchingFile = Paths.get("Starting..." /* ok ? */);
	private volatile int noOfArchivesSearched, noOfFilesSearched;

	ZipSearcher(Collection<String> searchPaths, Collection<String> searchFileExtns, boolean searchWithInArchives,
			String filePathToSearch, boolean caseSensitiveSearch)
	{
		this.searchPaths = searchPaths;
		this.swingAndNioFilePathFilter = new SwingAndNioFilePathFilter(caseSensitiveSearch, searchFileExtns);
		this.searchWithInArchives = searchWithInArchives;

		String[] pathAndExtn = getForwardSlashSeparatedPathAndExtn(filePathToSearch);

		String tmpFilePathWithoutExtnToSearch = pathAndExtn[0];
		Optional<String> mayBeFileExtn = Optional.ofNullable(pathAndExtn[1]);

		if (caseSensitiveSearch)
		{
			this.filePathWithoutExtnToSearch = tmpFilePathWithoutExtnToSearch;
			this.mayBeFileExtn = mayBeFileExtn;
		}
		else
		{
			this.filePathWithoutExtnToSearch = tmpFilePathWithoutExtnToSearch.toLowerCase();
			this.mayBeFileExtn = mayBeFileExtn.map(String::toLowerCase);
		}

		this.filePathWithoutExtnLen = filePathWithoutExtnToSearch.length();
		this.filePathWithoutExtnLenPlusOne = filePathWithoutExtnLen + 1;
		this.caseSensitiveSearch = caseSensitiveSearch;
	}

	void startSearch()
	{
		if (usedUp)
			throw new IllegalStateException("Can't reuse");
		usedUp = true;

		for (String searchPath : searchPaths)
		{
			Path path = Paths.get(searchPath);
			if (!swingAndNioFilePathFilter.accept(path))
				results.add(Result.err("Invalid input: " + path.toString()));
			else if (!Files.exists(path))
				results.add(Result.err("Not found: " + path.toString()));
			else if (!search(path))
				break;
		}
	}

	Path getCurrentSearchingFile()
	{
		return currentSearchingFile;
	}

	int getNoOfArchivesSearched()
	{
		return noOfArchivesSearched;
	}

	int getNoOfFilesSearched()
	{
		return noOfFilesSearched;
	}

	Collection<Result> getNewResults()
	{
		if (results.isEmpty())
			return Collections.emptySet();

		Collection<Result> newResults = new ArrayDeque<>();
		for (Result result; (result = results.poll()) != null; newResults.add(result));
		return newResults;
	}

	void cancelSearch()
	{
		cancelled = true;
	}

	private boolean search(Path path)
	{
		if (Files.isDirectory(path))
		{
			try (DirectoryStream<Path> ds = Files.newDirectoryStream(path, swingAndNioFilePathFilter))
			{
				for (Path child : ds)
					if (!search(child))
						return false;
			}
			catch (IOException e)
			{
				results.add(Result.err("Unable to read folder: " + path));
			}

			return true;
		}

		try (InputStream is = Files.newInputStream(path);
				ZipInputStream zis = new ZipInputStream(is) /* This buffers */)
		{
			if (!search(zis, path))
				return false;
		}
		catch (IOException e)
		{
			results.add(Result.err("IO Exception: " + e.getMessage() + ", while reading " + path));
		}

		return true;
	}

	/**
	 * @return true if search should continue (i.e. Not cancelled). Used in
	 *         recursion within this method
	 */
	private boolean search(ZipInputStream zis, Path filePath)
	{
		if (cancelled)
			return false;

		currentSearchingFile = filePath;

		try
		{
			for (ZipEntry zipEntry; (zipEntry = zis.getNextEntry()) != null;)
			{
				String originalEntryName = zipEntry.getName();
				String entryName = originalEntryName;

				if (!caseSensitiveSearch)
					entryName = entryName.toLowerCase();

				int filePathIndex = entryName.indexOf(filePathWithoutExtnToSearch);

				char c;
				// Note: Not checking if it is exactly the extension.
				// Fine if .(dot) or /(slash) are present.
				if ((filePathIndex != -1)
						&& (!mayBeFileExtn.isPresent() || (entryName.startsWith(mayBeFileExtn.get(), filePathIndex + filePathWithoutExtnLenPlusOne) &&
						(((c = entryName.charAt(filePathIndex + filePathWithoutExtnLen)) == '.') || c == '/'))))
				{
					results.add(Result.msg(filePath.resolve(originalEntryName.replace('/', File.separatorChar)).toString()));
				}

				// Note: Ignoring FindBugs warning as this is incremented by only one
				// thread
				noOfFilesSearched++;

				Optional<String> extn;
				if (searchWithInArchives && !zipEntry.isDirectory()
						&& (extn = Util.getExtension(entryName)).isPresent()
						&& swingAndNioFilePathFilter.acceptExtn(extn.get()))
					if (!search(new ZipInputStream(zis), filePath.resolve(originalEntryName.replace('/', File.separatorChar))))
						return false;
			}

			// Note: Ignoring FindBugs warning as this is incremented by only one
			// thread
			noOfArchivesSearched++;
		}
		catch (IOException e)
		{
			String zipOrIo = (e instanceof ZipException) ? "Zip" : "IO";
			results.add(Result.err(zipOrIo + " error occured while processing: " + filePath));
		}

		return true;
	}

	private static String[] getForwardSlashSeparatedPathAndExtn(String fileName)
	{
		int dotIndex = fileName.lastIndexOf('.');
		String extn;
		if ((dotIndex == -1)
				|| ((extn = fileName.substring(dotIndex + 1)).indexOf('/') != -1)
				|| (extn.indexOf('\\') != -1))
		{ // No file extension available
			return new String[] { fileName.replaceAll("[/\\\\]+", "/"), null };
		}
		else
		{
			return new String[] { fileName.substring(0, dotIndex).replaceAll("[./\\\\]+", "/"),
					fileName.substring(dotIndex + 1) };
		}
	}
}