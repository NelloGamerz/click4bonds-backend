package com.click4bonds.app.Modules.Storage.Exception;

import com.click4bonds.app.Modules.Common.Exceptions.BadRequestException;

/**
 * An upload was refused because of what the caller asked for, not because the
 * store failed.
 *
 * <p>Extends {@link BadRequestException} rather than {@code StorageException},
 * and the split is the point: the two failures have different remedies. A
 * {@code StorageException} means the bytes were fine and the store would not
 * take them — retrying may work, and the caller can do nothing. This means the
 * caller asked for something the upload policy does not allow — retrying
 * unchanged never will, and the caller is the one who fixes it.</p>
 *
 * <p>Kept in this module rather than reusing {@code BadRequestException}
 * directly so a caller can catch upload refusals specifically, and so
 * {@code GlobalExceptionHandler} can answer them with a code of their own
 * instead of the generic {@code BAD_REQUEST}.</p>
 *
 * <p>Raised in two places, both about a client-supplied upload: when a presigned
 * URL is asked for something the policy disallows, and when an object that
 * arrived under such a URL turns out not to match what was signed for.</p>
 */
public class UploadRejectedException extends BadRequestException {

    public UploadRejectedException(String message) {
        super(message);
    }
}
