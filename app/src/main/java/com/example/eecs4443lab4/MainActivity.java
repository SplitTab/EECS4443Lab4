package com.example.eecs4443lab4;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;

import java.io.ByteArrayOutputStream;

public class MainActivity extends AppCompatActivity {

    // UI
    private ImageView imageView;
    private TextView statusText;

    // Keep track of currently displayed image
    @Nullable private Bitmap currentBitmap = null; // Camera
    @Nullable private Uri currentUri = null;       // Gallery

    // Save/restore keys
    private static final String KEY_BITMAP = "key_bitmap_bytes";
    private static final String KEY_URI = "key_uri_string";

    // Gallery Picker - NO PERMISSION REQUIRED 
    private final ActivityResultLauncher<String> getContentLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    currentUri = uri;
                    currentBitmap = null;

                    imageView.setImageURI(uri);
                    setStatus("Selected from gallery");
                } else {
                    setStatus("Gallery canceled");
                }
            });

    // Camera - TakePicturePreview
    private final ActivityResultLauncher<Void> takePicturePreviewLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
                if (bitmap != null) {
                    currentBitmap = bitmap;
                    currentUri = null;

                    imageView.setImageBitmap(bitmap);
                    setStatus("Camera image captured");
                } else {
                    setStatus("Camera canceled");
                }
            });

    // Runtime camera permission
    private final ActivityResultLauncher<String> requestCameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    takePicturePreviewLauncher.launch(null);
                } else {
                    toast("Camera permission denied");
                    setStatus("Enable camera permission to take photos.");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.imageView);
        statusText = findViewById(R.id.statusText);
        Button btnTakePhoto = findViewById(R.id.btnTakePhoto);
        Button btnSelectGallery = findViewById(R.id.btnSelectGallery);

        btnTakePhoto.setOnClickListener(v -> handleTakePhoto());
        btnSelectGallery.setOnClickListener(v -> getContentLauncher.launch("image/*"));

        if (savedInstanceState != null) {
            restoreImageFromState(savedInstanceState);
        }
    }

    private void handleTakePhoto() {
        boolean hasPermission =
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        == PermissionChecker.PERMISSION_GRANTED;

        if (hasPermission) {
            takePicturePreviewLauncher.launch(null);
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    // Save image across rotation
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (currentBitmap != null) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            currentBitmap.compress(Bitmap.CompressFormat.PNG, 100, bos);
            outState.putByteArray(KEY_BITMAP, bos.toByteArray());
        }

        if (currentUri != null) {
            outState.putString(KEY_URI, currentUri.toString());
        }
    }

    private void restoreImageFromState(Bundle state) {
        byte[] bitmapBytes = state.getByteArray(KEY_BITMAP);
        String uriString = state.getString(KEY_URI);

        if (bitmapBytes != null) {
            Bitmap bmp = BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
            if (bmp != null) {
                currentBitmap = bmp;
                currentUri = null;
                imageView.setImageBitmap(bmp);
                setStatus("Restored camera image");
                return;
            }
        }

        if (uriString != null) {
            Uri uri = Uri.parse(uriString);
            currentUri = uri;
            currentBitmap = null;
            imageView.setImageURI(uri);
            setStatus("Restored gallery image");
        }
    }

    private void setStatus(String msg) {
        statusText.setText(msg);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
