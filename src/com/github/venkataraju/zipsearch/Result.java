package com.github.venkataraju.zipsearch;

final class Result
{
	final ResultType resultType;
	final String msg;

	static enum ResultType
	{
		MSG, ERR;
	}

	Result(ResultType resultType, String msg)
	{
		this.resultType = resultType;
		this.msg = msg;
	}

	static Result msg(String errorMessage)
	{
		return new Result(ResultType.MSG, errorMessage);
	}

	static Result err(String errorMessage)
	{
		return new Result(ResultType.ERR, errorMessage);
	}
}