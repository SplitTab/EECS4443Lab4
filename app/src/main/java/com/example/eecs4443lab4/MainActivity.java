package com.example.eecs4443lab4;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private ImageView imageView;
    private TextView statusText;

    @Nullable private Bitmap currentBitmap = null;
    @Nullable private Uri currentUri = null;

    private static final String KEY_BITMAP = "key_bitmap_bytes";
    private static final String KEY_URI = "key_uri_string";

    private final ActivityResultLauncher<Intent> getContentLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        currentUri = uri;
                        currentBitmap = null;

                        try {
                            Bitmap bitmap;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
                                bitmap = ImageDecoder.decodeBitmap(source);
                            } else {
                                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                            }

                            currentBitmap = bitmap;
                            imageView.setImageBitmap(bitmap);
                            setStatus("Loaded image via MediaStore");

                        } catch (IOException e) {
                            e.printStackTrace();
                            setStatus("Error loading image");
                        }
                    }
                } else {
                    setStatus("Gallery canceled");
                }
            });

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

    private final ActivityResultLauncher<String> requestCameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    takePicturePreviewLauncher.launch(null);
                } else {
                    toast("Camera permission denied");
                    setStatus("Enable camera permission to take photos.");
                }
            });

    private final ActivityResultLauncher<String> requestStoragePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    // Launch ACTION_GET_CONTENT intent
                    openGalleryWithActionGetContent();
                } else {
                    toast("Photos permission denied");
                    setStatus("Enable photos permission to select from gallery.");
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
        btnSelectGallery.setOnClickListener(v -> handleSelectFromGallery());

        if (savedInstanceState != null) {
            restoreImageFromState(savedInstanceState);
        }
    }

    private void handleTakePhoto() {
        boolean hasPermission =
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED;

        if (hasPermission) {
            takePicturePreviewLauncher.launch(null);
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void handleSelectFromGallery() {
        if (hasPhotoReadPermission()) {
            openGalleryWithActionGetContent();
        } else {
            requestStoragePermissionLauncher.launch(requiredPhotoPermission());
        }
    }

    private void openGalleryWithActionGetContent() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        getContentLauncher.launch(Intent.createChooser(intent, "Select an Image"));
    }

    private boolean hasPhotoReadPermission() {
        String perm = requiredPhotoPermission();
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED;
    }

    private String requiredPhotoPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            return Manifest.permission.READ_EXTERNAL_STORAGE;
        }
    }

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