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
    // status message gives guidance or context. Toasts are also used for live updates for error messages
    private TextView statusText;

    // stores the captured images (one is for the camera while other is for gallery image)
    // can be null so the program can decipher where the image was selected from
    @Nullable private Bitmap currentCamImg = null;
    @Nullable private Uri galImgUri = null;

    // keys so the image can be stored and restored when rotating the screen in bundle
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
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
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


    // check permission for photos and videos
    private final ActivityResultLauncher<String> storagePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    // if permission is granted then call the gallery launcher to select image
                    galleryLauncher.launch("image/*");
                } else {
                    // if not granted then relevant status and toast updates are made
                    toast("Photos permission denied");
                    setStatus("Enable photos permission to select from gallery.");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // store UI elements to relevant variables
        imageView = findViewById(R.id.imageView);
        statusText = findViewById(R.id.statusText);
        Button cameraButton = findViewById(R.id.btnTakePhoto);
        Button galleryButton = findViewById(R.id.btnSelectGallery);

        // add event listeners. onClick of a button, calls the relevant function
        cameraButton.setOnClickListener(v -> cameraHandler());
        galleryButton.setOnClickListener(v -> galleryHandler());

        // if there was a previous instance, then use the image from that instance by calling hte relevant function
        // this is useful when device is rotated as it can lose the image without this.
        if (savedInstanceState != null) {
            restoreImageFromState(savedInstanceState);
        }
    }

    // check if camera permissions are granted, if they are, then launches the camera, if not then launches the...
    // camera permission pop up by calling hte previously defined function
    private void cameraHandler() {
        boolean permit =
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED;

        if (permit) {
            cameraLauncher.launch(null);
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    // same thing as camera handler except this one is for gallery (photos or videos)
    private void galleryHandler() {
        if (checkGalleryPermit()) {
            // call the gallery launcher if permission is granted
            galleryLauncher.launch("image/*");
        } else {
            // else call the permission pop up
            storagePermissionLauncher.launch(galleryApiCaller());
        }
    }

    // check if permit is there or not for the gallery
    private boolean checkGalleryPermit() {
        String permitType = galleryApiCaller();
        return ContextCompat.checkSelfPermission(this, permitType) == PackageManager.PERMISSION_GRANTED;
    }

    // returns the relevant code based on the API of the device in use
    private String galleryApiCaller() {
        // for versions newer than  or equal to android 13
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            // for older ones
            return Manifest.permission.READ_EXTERNAL_STORAGE;
        }
    }

    // for temporarily saving the image on device rotation, uses callback function that is called whenever...
    // a state is about to be destroyed
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // if image was from cam, then use byteArray and then save it in bundle (which is essentially a hashmap which is why
        // a key is being used)
        if (currentCamImg != null) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            currentCamImg.compress(Bitmap.CompressFormat.PNG, 100, bos);
            outState.putByteArray(CAM_KEY, bos.toByteArray());
        }

        // else if it was from gallery then putString
        if (galImgUri != null) {
            outState.putString(GAL_KEY, galImgUri.toString());
        }
    }

    // restore from saved bundle if it exists
    private void restoreImageFromState(Bundle state) {
        // getting the bundles using the keys
        byte[] bitmapBytes = state.getByteArray(CAM_KEY);
        String uriString = state.getString(GAL_KEY);

        // if there was an image saved from camera, then restore it into a bitmap
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
        // if the image was from gallery then use uri
        if (uriString != null) {
            Uri uri = Uri.parse(uriString);
            galImgUri = uri;
            currentCamImg = null;
            imageView.setImageURI(uri);
            setStatus("Restored gallery image");
        }
    }

    // to update status
    private void setStatus(String msg) {
        statusText.setText(msg);
    }

    // for toast popup
    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
