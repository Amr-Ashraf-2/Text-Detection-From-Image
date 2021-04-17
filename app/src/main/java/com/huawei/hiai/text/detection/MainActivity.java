package com.huawei.hiai.text.detection;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.huawei.hiai.vision.common.ConnectionCallback;
import com.huawei.hiai.vision.common.VisionBase;
import com.huawei.hiai.vision.text.TextDetector;
import com.huawei.hiai.vision.visionkit.common.Frame;
import com.huawei.hiai.vision.visionkit.text.Text;
import com.huawei.hiai.vision.visionkit.text.TextBlock;
import com.huawei.hiai.vision.visionkit.text.TextConfiguration;
import com.huawei.hiai.vision.visionkit.text.TextDetectType;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.Scanner;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "generic text";
    private static final int REQUEST_CHOOSE_PHOTO_CODE = 2;

    private Bitmap mBitmap;
    private ImageView mImageView;
    private EditText mEditTxtResult;
    protected ProgressDialog dialog;
    private TextDetector mTextDetector;
    private TextView mTxtView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        requestPermissions();
        visionInit();
    }

    private void initView() {
        mEditTxtResult = findViewById(R.id.result);
        mImageView = findViewById(R.id.imgViewPicture);
        mTxtView = findViewById(R.id.txtInImage);
    }

    private void visionInit() {
        VisionBase.init(this, new ConnectionCallback() {
            @Override
            public void onServiceConnect() {
                Log.e(TAG, " onServiceConnect");
            }

            @Override
            public void onServiceDisconnect() {
                Log.e(TAG, " onServiceDisconnect");
            }
        });
    }

    public void onChildClick(View view) {
        switch (view.getId()) {
            case R.id.btnSelect: {
                Log.d(TAG, "Select an image");
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                startActivityForResult(intent, REQUEST_CHOOSE_PHOTO_CODE);
                break;
            }
        }
    }

    private void showDialog() {
        if (dialog == null) {
            dialog = new ProgressDialog(MainActivity.this);
            dialog.setTitle("Predicting...");
            //dialog.setMessage("Wait for one sec...");
            dialog.setMessage("Please Wait...");
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
        }
        dialog.show();
    }

    private static class GenericTextAsync extends AsyncTask<Bitmap, String, Text> {
        private WeakReference<MainActivity> reference;

        private GenericTextAsync(WeakReference<MainActivity> activityWeakReference) {
            reference = activityWeakReference;
        }

        @Override
        protected Text doInBackground(Bitmap... bitmap) {
            MainActivity activity = reference.get();
            activity.mTextDetector = new TextDetector(activity);
            Frame frame = new Frame();
            frame.setBitmap(activity.mBitmap);
            /* create a TextDetector instance firstly */
            //TextDetector textDetector = new TextDetector(mContext);
            /* create a TextConfiguration instance here, */
            TextConfiguration config = new TextConfiguration();
            /* and set the EngineType as focus shoot ocr */
            config.setEngineType(TextDetectType.TYPE_TEXT_DETECT_FOCUS_SHOOT);
            activity.mTextDetector.setTextConfiguration(config);

            /* start to detect and get the json object, which can be analyzed as Text */
            JSONObject jsonObject = activity.mTextDetector.detect(frame, null);
            Log.d(TAG, "end to detect ocr. json: " + jsonObject.toString());  /*jsonObject never be null*/

            /* analyze the result */
            return activity.mTextDetector.convertResult(jsonObject);
        }

        @Override
        protected void onPostExecute(Text result) {
            MainActivity activity = reference.get();
            activity.dialog.dismiss();
            if (result == null) {
                activity.mEditTxtResult.setText("Failed to detect text lines, result is null.");
                activity.mTxtView.setVisibility(View.VISIBLE);
                activity.mEditTxtResult.setVisibility(View.VISIBLE);
                return;
            }
            String textValue = result.getValue();

            Log.d(TAG, "OCR Detection succeeded.");
            //activity.mTxtViewResult.setText("Text in image: " + textValue);
            activity.mEditTxtResult.setText(textValue);

            activity.mTxtView.setVisibility(View.VISIBLE);
            activity.mEditTxtResult.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CHOOSE_PHOTO_CODE && resultCode == Activity.RESULT_OK) {
            if (data == null) {
                return;
            }

            Uri selectedImage = data.getData();
            getBitmap(selectedImage);

            if (mBitmap == null) {
                Toast.makeText(this, "Please select an image", Toast.LENGTH_SHORT).show();
                return;
            }
            mEditTxtResult.setVisibility(View.GONE);
            mTxtView.setVisibility(View.GONE);
            mEditTxtResult.setText("");
            showDialog();
            new GenericTextAsync(new WeakReference<>(this)).execute(mBitmap);
        }
    }

    private void requestPermissions() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int permission1 = ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE);
                int permission2 = ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.CAMERA);
                if (permission1 != PackageManager.PERMISSION_GRANTED || permission2 != PackageManager
                        .PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA}, 0x0010);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void getBitmap(Uri imageUri) {
        String[] pathColumn = {MediaStore.Images.Media.DATA};

        Cursor cursor = getContentResolver().query(imageUri, pathColumn, null, null, null);
        if (cursor == null) return;
        cursor.moveToFirst();
        int columnIndex = cursor.getColumnIndex(pathColumn[0]);
        /* get image path */
        String picturePath = cursor.getString(columnIndex);
        cursor.close();

        mBitmap = BitmapFactory.decodeFile(picturePath);
        if (mBitmap == null) {
            return;
        }
        mImageView.setImageBitmap(mBitmap);
        mTxtView.setVisibility(View.GONE);
        mEditTxtResult.setVisibility(View.GONE);
        mEditTxtResult.setText("");
    }

    private void dismissDialog() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length <= 0
                || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        /* release ocr instance and free the npu resources*/
        if (mTextDetector != null) {
            mTextDetector.release();
        }
        dismissDialog();
        if (mBitmap != null) {
            mBitmap.recycle();
        }
    }
}
