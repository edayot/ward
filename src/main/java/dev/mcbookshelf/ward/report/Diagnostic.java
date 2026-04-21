package dev.mcbookshelf.ward.report;

/**
 * A load diagnostic: a datapack file that failed to load, with the registry kind and resource id
 * it belongs to.
 */
public record Diagnostic(Severity severity, String type, String id, String message) {
	public enum Severity {
		Error, Warn
	}

	public static Diagnostic error(String type, String id, String message) {
		return new Diagnostic(Severity.Error, type, id, message);
	}

	public static Diagnostic warn(String type, String id, String message) {
		return new Diagnostic(Severity.Warn, type, id, message);
	}

	/**
	 * Extracts a human-readable message from a throwable, stripping the leading exception class name
	 * that wrapped exceptions embed in their message.
	 */
	public static String describe(Throwable error) {
		String message = error.getMessage();
		return message == null
				? error.getClass().getSimpleName()
				: message.replaceFirst("^[A-Za-z0-9.$]+(Exception|Error): ", "");
	}
}
