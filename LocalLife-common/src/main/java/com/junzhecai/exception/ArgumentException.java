
package com.junzhecai.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class ArgumentException extends BaseException {
	
	private List<ArgumentError> argumentErrorList;
	
	public ArgumentException(List<ArgumentError> argumentErrorList) {
		this.argumentErrorList = argumentErrorList;
	}

	public ArgumentException(String message) {
		super(message);
	}
	

	public ArgumentException(Throwable cause) {
		super(cause);
	}

	public ArgumentException(String message, Throwable cause) {
		super(message, cause);
	}
}
