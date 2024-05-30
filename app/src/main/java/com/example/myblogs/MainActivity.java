package com.example.myblogs;
import android.os.Environment;

import android.Manifest;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private EditText textName, textBody, searchEditText;
    private Button addBtn, uploadImageBtn, selectAllBtn, deleteSelectedBtn, actionBtn;
    private LinearLayout blogsContainer;
    private ImageView imageView;
    private SQLiteDatabase db;

    private static final int BLOG_DETAIL_REQUEST_CODE = 1;
    private static final int ACTION_SEARCH = 1;
    private static final int ACTION_CLEAR = 2;
    private int currentAction = ACTION_SEARCH;

    private static final int REQUEST_CODE_READ_EXTERNAL_STORAGE = 100;
    private static final int REQUEST_CODE_PICK_IMAGE = 101;
    private static final int REQUEST_CODE_CAMERA = 102;
    private String currentPhotoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textName = findViewById(R.id.textName);
        textBody = findViewById(R.id.textBody);
        addBtn = findViewById(R.id.addBtn);
        uploadImageBtn = findViewById(R.id.uploadImageBtn);
        selectAllBtn = findViewById(R.id.selectAllBtn);
        deleteSelectedBtn = findViewById(R.id.deleteSelectedBtn);
        blogsContainer = findViewById(R.id.blogsContainer);
        searchEditText = findViewById(R.id.searchEditText);
        actionBtn = findViewById(R.id.searchBtn);
        imageView = findViewById(R.id.imageView);

        db = openOrCreateDatabase("blogsdb", MODE_PRIVATE, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS blogtable (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, body TEXT)");

        addBtn.setOnClickListener(view -> addBlog());

        uploadImageBtn.setOnClickListener(view -> {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA},
                        REQUEST_CODE_READ_EXTERNAL_STORAGE);
            } else {
                showImagePickerDialog();
            }
        });

        selectAllBtn.setOnClickListener(view -> selectAllBlogs());

        deleteSelectedBtn.setOnClickListener(view -> deleteSelectedBlogs());

        actionBtn.setOnClickListener(view -> {
            if (currentAction == ACTION_SEARCH) {
                performSearch();
            } else if (currentAction == ACTION_CLEAR) {
                clearSearch();
            }
        });

        displayBlogs();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_READ_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showImagePickerDialog();
            } else {
                Toast.makeText(this, "Permission denied to read your External storage", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showImagePickerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Image");
        builder.setItems(new CharSequence[]{"Camera", "Gallery"}, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    openCamera();
                } else {
                    openImagePicker();
                }
            }
        });
        builder.show();
    }

    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Log.e("MainActivity", "Error occurred while creating the File", ex);
            }
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.example.myblogs.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_CODE_CAMERA);
            }
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                imageView.setImageURI(imageUri); // Display the image in an ImageView
            }
        }
        if (requestCode == REQUEST_CODE_CAMERA && resultCode == RESULT_OK) {
            File file = new File(currentPhotoPath);
            Uri imageUri = Uri.fromFile(file);
            imageView.setImageURI(imageUri);
        }
        if (requestCode == BLOG_DETAIL_REQUEST_CODE && resultCode == RESULT_OK) {
            displayBlogs();
        }
    }

    private void addBlog() {
        String name = textName.getText().toString().trim();
        String body = textBody.getText().toString().trim();

        if (name.isEmpty() || body.isEmpty()) {
            Toast.makeText(this, "Please enter both name and body", Toast.LENGTH_SHORT).show();
            return;
        }

        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("body", body);

        long result = db.insert("blogtable", null, values);

        if (result != -1) {
            Toast.makeText(this, "Blog added successfully", Toast.LENGTH_SHORT).show();
            textName.getText().clear();
            textBody.getText().clear();
            displayBlogs();
        } else {
            Toast.makeText(this, "Error adding blog", Toast.LENGTH_SHORT).show();
        }
    }

    private void displayBlogs() {
        blogsContainer.removeAllViews();
        Cursor cursor = db.rawQuery("SELECT * FROM blogtable", null);
        displayCursorBlogs(cursor);
        cursor.close();
    }

    private void displayCursorBlogs(Cursor cursor) {
        int idColumnIndex = cursor.getColumnIndex("id");
        int nameColumnIndex = cursor.getColumnIndex("name");
        int bodyColumnIndex = cursor.getColumnIndex("body");

        while (cursor.moveToNext()) {
            if (idColumnIndex != -1 && nameColumnIndex != -1 && bodyColumnIndex != -1) {
                int id = cursor.getInt(idColumnIndex);
                String name = cursor.getString(nameColumnIndex);
                String body = cursor.getString(bodyColumnIndex);

                RelativeLayout blogEntryLayout = createBlogEntryLayout(id, name, body);
                blogsContainer.addView(blogEntryLayout);
            } else {
                Log.e("MainActivity", "Invalid column indices");
            }
        }
    }

    private RelativeLayout createBlogEntryLayout(int id, String name, String body) {
        RelativeLayout blogEntryLayout = new RelativeLayout(this);
        blogEntryLayout.setPadding(0, 0, 0, 20);

        CheckBox checkBox = new CheckBox(this);
        checkBox.setId(id);

        TextView blogTextView = new TextView(this);
        blogTextView.setText("Blog Name: " + name + "\n\nBlog Body: " + body + "\n\n");
        blogTextView.setTextSize(16);
        blogTextView.setTypeface(null, Typeface.BOLD);

        Button selectBtn = new Button(this);
        selectBtn.setText("Select");
        selectBtn.setOnClickListener(view -> selectIndividualBlog(id));

        Button showBtn = new Button(this);
        showBtn.setText("Show");
        showBtn.setOnClickListener(view -> showBlogDetails(id, name, body));

        RelativeLayout.LayoutParams paramsCheckBox = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        paramsCheckBox.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        paramsCheckBox.setMargins(0, 0, 2, 0);

        RelativeLayout.LayoutParams paramsBlogText = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        paramsBlogText.addRule(RelativeLayout.CENTER_HORIZONTAL);

        RelativeLayout.LayoutParams paramsSelectBtn = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        paramsSelectBtn.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        paramsSelectBtn.setMargins(0, 0, 2, 0);

        RelativeLayout.LayoutParams paramsShowBtn = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        paramsShowBtn.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        paramsShowBtn.addRule(RelativeLayout.BELOW, blogTextView.getId());
        paramsShowBtn.setMargins(0, 8, 2, 0);

        blogEntryLayout.addView(checkBox, paramsCheckBox);
        blogEntryLayout.addView(blogTextView, paramsBlogText);
        blogEntryLayout.addView(selectBtn, paramsSelectBtn);
        blogEntryLayout.addView(showBtn, paramsShowBtn);

        return blogEntryLayout;
    }

    private void selectAllBlogs() {
        for (int i = 0; i < blogsContainer.getChildCount(); i++) {
            View child = blogsContainer.getChildAt(i);
            if (child instanceof RelativeLayout) {
                RelativeLayout blogEntryLayout = (RelativeLayout) child;
                for (int j = 0; j < blogEntryLayout.getChildCount(); j++) {
                    View innerChild = blogEntryLayout.getChildAt(j);
                    if (innerChild instanceof CheckBox) {
                        CheckBox checkBox = (CheckBox) innerChild;
                        checkBox.setChecked(true);
                    }
                }
            }
        }
    }

    private void selectIndividualBlog(int blogId) {
        CheckBox checkBox = findViewById(blogId);
        if (checkBox != null) {
            checkBox.setChecked(true);
        }
    }

    private void deleteSelectedBlogs() {
        for (int i = 0; i < blogsContainer.getChildCount(); i++) {
            View child = blogsContainer.getChildAt(i);
            if (child instanceof RelativeLayout) {
                RelativeLayout blogEntryLayout = (RelativeLayout) child;
                for (int j = 0; j < blogEntryLayout.getChildCount(); j++) {
                    View innerChild = blogEntryLayout.getChildAt(j);
                    if (innerChild instanceof CheckBox) {
                        CheckBox checkBox = (CheckBox) innerChild;
                        if (checkBox.isChecked()) {
                            int blogId = checkBox.getId();
                            deleteBlog(blogId);
                        }
                    }
                }
            }
        }

        displayBlogs();
    }

    private void deleteBlog(int blogId) {
        int result = db.delete("blogtable", "id=?", new String[]{String.valueOf(blogId)});

        if (result > 0) {
            Toast.makeText(this, "Blog deleted successfully", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Error deleting blog", Toast.LENGTH_SHORT).show();
        }
    }

    private void showBlogDetails(int blogId, String blogName, String blogBody) {
        Intent intent = new Intent(this, BlogDetailActivity.class);
        intent.putExtra("blogId", blogId);
        intent.putExtra("blogName", blogName);
        intent.putExtra("blogBody", blogBody);
        startActivityForResult(intent, BLOG_DETAIL_REQUEST_CODE);
    }

    private void performSearch() {
        String searchQuery = searchEditText.getText().toString().trim();
        blogsContainer.removeAllViews();

        Cursor cursor = db.rawQuery("SELECT * FROM blogtable WHERE name LIKE ?", new String[]{"%" + searchQuery + "%"});
        displayCursorBlogs(cursor);
        cursor.close();

        actionBtn.setText("Clear");
        currentAction = ACTION_CLEAR;
    }

    private void clearSearch() {
        displayBlogs();
        actionBtn.setText("Search");
        currentAction = ACTION_SEARCH;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (db != null && db.isOpen()) {
            db.close();
        }
    }
}
