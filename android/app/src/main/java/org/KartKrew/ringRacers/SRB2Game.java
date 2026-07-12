package org.kartkrew.srb2k;

import org.libsdl.app.SDLActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.Settings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class SRB2Game extends SDLActivity {

	private static final String PREFS_NAME = "srb2k_prefs";
	private static final String KEY_DATA_PATH = "data_folder_path";
	private static final int REQUEST_PICK_FOLDER = 1001;
	private static final int REQUEST_MANAGE_STORAGE = 1002;
	private static final int REQUEST_LEGACY_STORAGE_PERMISSION = 1003;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState); // always initialize properly first

		SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
		String savedPath = prefs.getString(KEY_DATA_PATH, null);

		if (savedPath != null) {
			writePathForNative(savedPath);
			return;
		}

		// First boot: make sure we have real storage access before letting
		// the user pick a folder, since the picker alone doesn't grant raw
		// fopen()-level access to non-primary storage (e.g. SD cards).
		if (hasFullStorageAccess()) {
			openFolder();
		} else {
			requestFullStorageAccess();
		}
	}

	private boolean hasFullStorageAccess() {
		if (Build.VERSION.SDK_INT >= 30) {
			return Environment.isExternalStorageManager();
		} else {
			return checkPermission("android.permission.WRITE_EXTERNAL_STORAGE");
		}
	}

	private void requestFullStorageAccess() {
		if (Build.VERSION.SDK_INT >= 30) {
			Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
			intent.setData(Uri.parse("package:" + getPackageName()));
			startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
		} else {
			requestPermissions(
					new String[]{"android.permission.WRITE_EXTERNAL_STORAGE"},
					REQUEST_LEGACY_STORAGE_PERMISSION
			);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		if (requestCode == REQUEST_LEGACY_STORAGE_PERMISSION) {
			boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
			if (granted) {
				openFolder();
			} else {
				showAccessDeniedFallback();
			}
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == REQUEST_MANAGE_STORAGE) {
			if (hasFullStorageAccess()) {
				openFolder();
			} else {
				showAccessDeniedFallback();
			}
			return;
		}

		if (requestCode == REQUEST_PICK_FOLDER) {
			String resolvedPath = null;

			if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
				Uri treeUri = data.getData();

				getContentResolver().takePersistableUriPermission(
						treeUri,
						Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
				);

				resolvedPath = resolveTreeUriToRealPath(treeUri);
			}

			if (resolvedPath == null) {
				resolvedPath = getExternalFilesDir(null).getAbsolutePath();
			}

			SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
			prefs.edit().putString(KEY_DATA_PATH, resolvedPath).apply();

			writePathForNative(resolvedPath);
		}
	}

	/**
	 * Storage access wasn't granted - fall back to app storage rather than
	 * leaving the user stuck with no path configured at all.
	 */
	private void showAccessDeniedFallback() {
		String fallback = getExternalFilesDir(null).getAbsolutePath();
		SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
		prefs.edit().putString(KEY_DATA_PATH, fallback).apply();
		writePathForNative(fallback);

		new AlertDialog.Builder(this)
				.setTitle("Storage access not granted")
				.setMessage("Using the app's default storage location instead. "
						+ "You can grant full storage access later in Android's app settings "
						+ "if you'd like to use a custom folder, including an SD card.")
				.setCancelable(false)
				.setPositiveButton("OK", null)
				.show();
	}

	/**
	 * Converts a SAF tree URI into a real filesystem path.
	 * Works for primary storage and real SD cards.
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
