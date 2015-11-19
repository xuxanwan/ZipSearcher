package com.github.venkataraju.zipsearch;

import java.util.Optional;

final class Util
{
	static Optional<String> getExtension(String fileName)
	{
		int dotIndex = fileName.lastIndexOf('.') + 1;
		return (dotIndex == 0) ? Optional.empty() : Optional.of(fileName.substring(dotIndex));
	}
}