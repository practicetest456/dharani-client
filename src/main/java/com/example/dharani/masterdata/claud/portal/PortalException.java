package com.example.dharani.masterdata.claud.portal;

/** The Bhubharati portal could not be reached or did not return usable data. */
public class PortalException extends RuntimeException {

	public PortalException(String message) {
		super(message);
	}

	public PortalException(String message, Throwable cause) {
		super(message, cause);
	}
}
