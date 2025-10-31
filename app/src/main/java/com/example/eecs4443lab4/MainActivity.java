package com.example.eecs4443lab4;

import android.Manifest;
import android.content.pm.PackageManager;
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

import java.io.ByteArrayOutputStream;

public class MainActivity extends AppCompatActivity {

    // stores ui elements
    private ImageView imageView;
    private TextView statusText;

    // stores the captured images (one is for the camera while other is for gallery image)
    // can be null so the program can decipher where the image was selected from
    @Nullable private Bitmap currentCamImg = null;
    @Nullable private Uri galImgUri = null;

    // keys so the image can be stored and restored when rotating the screen
    private static final String CAM_KEY = "camera_key";
    private static final String GAL_KEY = "gallery_key";

    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                // if an image is selected from gallery, then store it in the gal variable (camera one is set to null
                // so the code can eventually decipher whether image is from camera of gallery)
                if (uri != null) {
                    galImgUri = uri;
                    currentCamImg = null;

                    // assigns the image to the ui for display
                    imageView.setImageURI(uri);

                    // updates the status
                    setStatus("Selected from gallery");
                } else {
                    // if gallery action was canceled, then shows relevant status
                    setStatus("Gallery action canceled");
                }
            });

    // camera launcher, works similar to the gallery code except uses bitmaps instead of uri
    private final ActivityResultLauncher<Void> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
                if (bitmap != null) {
                    currentCamImg = bitmap;
                    galImgUri = null;

                    imageView.setImageBitmap(bitmap);
                    setStatus("Camera image captured");
                } else {
                    setStatus("Camera action canceled");
                }
            });

    // check permission for camera
    private final ActivityResultLauncher<String> cameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                // if permission granted then launch camera
                if (granted) {
                    cameraLauncher.launch(null);
                } else {
                    // else bring up a toast that says permission was denied, then change the status
                    toast("Camera permission denied");
                    setStatus("Enable camera permission to take photos.");
                }
            });

    // ---- Runtime STORAGE/PHOTOS permission (READ_MEDIA_IMAGES or READ_EXTERNAL_STORAGE) ----
    private final ActivityResultLauncher<String> requestStoragePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    // Now that we have permission, open the gallery
                    galleryLauncher.launch("image/*");
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

    // ----- Camera flow -----
    private void handleTakePhoto() {
        boolean hasPermission =
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED;

        if (hasPermission) {
            cameraLauncher.launch(null);
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    // ----- Gallery flow with explicit permission request -----
    private void handleSelectFromGallery() {
        if (hasPhotoReadPermission()) {
            // Permission already granted -> open picker
            galleryLauncher.launch("image/*");
        } else {
            // Request the right permission for the OS version
            requestStoragePermissionLauncher.launch(requiredPhotoPermission());
        }
    }

    private boolean hasPhotoReadPermission() {
        String perm = requiredPhotoPermission();
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED;
    }

    private String requiredPhotoPermission() {
        // Android 13+ needs READ_MEDIA_IMAGES; older versions use READ_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            return Manifest.permission.READ_EXTERNAL_STORAGE;
        }
    }

    // ----- Save/restore image across rotation -----
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (currentCamImg != null) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            currentCamImg.compress(Bitmap.CompressFormat.PNG, 100, bos);
            outState.putByteArray(CAM_KEY, bos.toByteArray());
        }

        if (galImgUri != null) {
            outState.putString(GAL_KEY, galImgUri.toString());
        }
    }

    private void restoreImageFromState(Bundle state) {
        byte[] bitmapBytes = state.getByteArray(CAM_KEY);
        String uriString = state.getString(GAL_KEY);

        if (bitmapBytes != null) {
            Bitmap bmp = BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
            if (bmp != null) {
                currentCamImg = bmp;
                galImgUri = null;
                imageView.setImageBitmap(bmp);
                setStatus("Restored camera image");
                return;
            }
        }

        if (uriString != null) {
            Uri uri = Uri.parse(uriString);
            galImgUri = uri;
            currentCamImg = null;
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
