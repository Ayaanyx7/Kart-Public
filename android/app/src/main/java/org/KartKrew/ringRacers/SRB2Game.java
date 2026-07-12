package org.kartkrew.srb2k;

import org.libsdl.app.SDLActivity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class SRB2Game extends SDLActivity {

	private static final String PREFS_NAME = "srb2k_prefs";
	private static final String KEY_DATA_PATH = "data_folder_path";
	private static final int REQUEST_PICK_FOLDER = 1001;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState); // always initialize properly first

		SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
		String savedPath = prefs.getString(KEY_DATA_PATH, null);

		if (savedPath == null) {
			openFolder(); // Activity is fully valid now and will survive backgrounding
		} else {
			writePathForNative(savedPath);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == REQUEST_PICK_FOLDER) {
			String resolvedPath = null;

			if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
				Uri treeUri = data.getData();

				// Persist permission so this URI stays valid across app restarts
				getContentResolver().takePersistableUriPermission(
						treeUri,
						Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
				);

				resolvedPath = resolveTreeUriToRealPath(treeUri);
			}

			if (resolvedPath == null) {
				// User cancelled, or the URI couldn't be resolved to a real path
				// (e.g. an exotic storage provider) - fall back to the app's
				// existing known-good storage location rather than getting stuck
				resolvedPath = getExternalFilesDir(null).getAbsolutePath();
			}

			SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
			prefs.edit().putString(KEY_DATA_PATH, resolvedPath).apply();

			writePathForNative(resolvedPath);
		}
	}

	/**
	 * Converts a SAF tree URI into a real filesystem path.
	 * Works for primary storage and real SD cards (the vast majority of cases).
	 * Returns null if the URI can't be resolved this way.
	 */
	private String resolveTreeUriToRealPath(Uri treeUri) {
		try {
			String docId = DocumentsContract.getTreeDocumentId(treeUri);
			String[] split = docId.split(":");
			String type = split[0];
			String relativePath = split.length > 1 ? split[1] : "";

			if ("primary".equalsIgnoreCase(type)) {
				return "/storage/emulated/0" + (relativePath.isEmpty() ? "" : "/" + relativePath);
			} else {
				// Real SD card - type is usually the volume UUID
				return "/storage/" + type + (relativePath.isEmpty() ? "" : "/" + relativePath);
			}
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Writes the resolved path to a plain file that native code (plain fopen,
	 * no JNI needed) can read on startup - stored in the app's own guaranteed-
	 * accessible storage, not the user-picked folder itself.
	 */
	private void writePathForNative(String path) {
		try {
			File marker = new File(getExternalFilesDir(null), "data_path.txt");
			FileOutputStream fos = new FileOutputStream(marker);
			fos.write(path.getBytes());
			fos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static boolean checkPermission(String permission) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
			return true;
		}
		Activity activity = (Activity)getContext();
		if (activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED)
			return true;
		return false;
	}


	public static boolean inMultiWindowMode() {
		if (Build.VERSION.SDK_INT >= 24) {
			if (SRB2Game.mSingleton.isInMultiWindowMode())
				return true;
		}
		return false;
	}

	public void openFolder()
	{
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		startActivityForResult(intent, REQUEST_PICK_FOLDER);
	}
}
