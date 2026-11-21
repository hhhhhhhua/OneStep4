package com.sangluo.onestep.feature.audio;

import com.sangluo.onestep.system.root.ShellCommandResult;

import java.util.Locale;

/**
 * Builds and inspects the privileged {@code cmd audio} calls that route the media
 * strategy. The preferred-device API is a system API, so it is reached through the
 * app's root/Shizuku shell instead of reflection.
 */
public final class AudioOutputRoutingClient {
    public static final String HELP_COMMAND = "cmd audio";
    public static final String PREFERRED_COMMAND = "cmd audio set-preferred-output-device ";
    public static final String AUTO = "AUTO";

    private AudioOutputRoutingClient() {
    }

    public static String preferredCommand(String typeName, String address) {
        StringBuilder command = new StringBuilder(PREFERRED_COMMAND)
                .append(typeName == null ? "" : typeName.trim());
        if (!isRedactedAddress(address)) {
            command.append(' ').append(shellQuote(address.trim()));
        }
        return command.toString();
    }

    /**
     * Android redacts Bluetooth addresses for apps without {@code BLUETOOTH_CONNECT}
     * (for example {@code XX:XX:XX:XX:11:DF}). Passing such a value to
     * {@code set-preferred-output-device} fails, so it must be omitted and the system
     * chooses the active device of that type.
     */
    public static boolean isRedactedAddress(String address) {
        if (address == null) {
            return true;
        }
        String upper = address.trim().toUpperCase(Locale.US);
        return upper.isEmpty() || upper.contains("XX") || "00:00:00:00:00:00".equals(upper);
    }

    public static String autoCommand() {
        return PREFERRED_COMMAND + AUTO;
    }

    /** True when the help text exposes the preferred-output subcommand. */
    public static boolean supportsPreferredOutput(String helpOutput) {
        return helpOutput != null && helpOutput.contains("set-preferred-output-device");
    }

    /**
     * The command prints "successfully set preferred device" on success and an error line
     * otherwise; exit codes are not reliable across ROM builds.
     */
    public static boolean preferredApplied(ShellCommandResult result) {
        if (result == null) {
            return false;
        }
        String output = result.output == null ? "" : result.output;
        String lower = output.toLowerCase(Locale.US);
        if (lower.contains("can't find service") || lower.contains("unknown command")
                || lower.contains("error") || lower.contains("failed")
                || lower.contains("invalid")) {
            return false;
        }
        return result.isSuccess() || lower.contains("successfully");
    }

    /**
     * Releasing the preference may answer "failed to set to default" when no preference
     * was active; that is not an error for our purposes.
     */
    public static boolean preferredCleared(ShellCommandResult result) {
        if (result == null) {
            return false;
        }
        String output = result.output == null ? "" : result.output;
        String lower = output.toLowerCase(Locale.US);
        return !lower.contains("can't find service") && !lower.contains("unknown command")
                && !lower.contains("invalid") && !lower.contains("error");
    }

    /** Short failure text for the picker dialog. */
    public static String failureMessage(ShellCommandResult result) {
        if (result == null) {
            return "无法执行音频路由命令，请检查 ROOT/Shizuku 授权";
        }
        String output = result.output == null ? "" : result.output.trim();
        if (output.isEmpty()) {
            return "音频路由命令失败（exit=" + result.exitCode + "）";
        }
        return output.length() > 120 ? output.substring(0, 120) : output;
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
