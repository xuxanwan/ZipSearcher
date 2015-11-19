package com.github.venkataraju.zipsearch;

import java.io.File;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.filechooser.FileFilter;

/** Acts as both {@link FileFilter} and {@link Filter} */
final class SwingAndNioFilePathFilter extends FileFilter implements Filter<Path>
{
	private final boolean caseSensitive;
	private final Set<String> extns;

	/**
	 * @param caseSensitive
	 *          true - If {@code extns} should be considered as case sensitive
	 */
	SwingAndNioFilePathFilter(boolean caseSensitive, Collection<String> extns)
	{
		this.caseSensitive = caseSensitive;
		this.extns = extns.stream()
				.map(str -> caseSensitive ? str : str.toLowerCase())
				.collect(Collectors.toCollection(HashSet::new));
	}

	@Override
	public boolean accept(File file)
	{
		if (file.isDirectory())
			return true;
		Optional<String> extn = Util.getExtension(file.getName());
		return extn.isPresent() && extns.contains(caseSensitive ? extn.get() : extn.get().toLowerCase());
	}

	@Override
	public boolean accept(Path path)
	{
		if (Files.isDirectory(path))
			return true;
		Optional<String> extn = Optional.ofNullable(path.getFileName())
				.map(Object::toString)
				.flatMap(Util::getExtension);
		return extn.isPresent() && extns.contains(caseSensitive ? extn.get() : extn.get().toLowerCase());
	}

	public boolean acceptExtn(String extn)
	{
		return extns.contains(caseSensitive ? extn : extn.toLowerCase());
	}

	@Override
	public String getDescription()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Directories");
		if (!extns.isEmpty())
			sb.append(" + ");
		sb.append(String.join(", ", extns));
		sb.append(" files (Case " + (caseSensitive ? "S" : "Ins") + "ensitive)");
		return sb.toString();
	}
}