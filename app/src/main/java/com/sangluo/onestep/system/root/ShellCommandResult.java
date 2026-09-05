package com.sangluo.onestep.system.root;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

/** Result returned by a privileged shell command. */
public final class ShellCommandResult implements Parcelable {
    public final int exitCode;
    public final String output;

    public ShellCommandResult(int exitCode, String output) {
        this.exitCode = exitCode;
        this.output = output;
    }

    protected ShellCommandResult(Parcel in) {
        exitCode = in.readInt();
        output = in.readString();
    }

    public static final Creator<ShellCommandResult> CREATOR =
            new Creator<ShellCommandResult>() {
                @Override
                public ShellCommandResult createFromParcel(Parcel in) {
                    return new ShellCommandResult(in);
                }

                @Override
                public ShellCommandResult[] newArray(int size) {
                    return new ShellCommandResult[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(exitCode);
        dest.writeString(output);
    }

    public boolean isSuccess() {
        return exitCode == 0;
    }
}