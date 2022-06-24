package com.example.image_classification

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.example.image_classification.databinding.ActivityMainBinding
import com.example.image_classification.ml.BirdsModel
import org.tensorflow.lite.support.image.TensorImage
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var imageView: ImageView
    private lateinit var button: Button
    private lateinit var tvOutput: TextView
    private val GALLERY_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        imageView = binding.imageView
        button = binding.btnCaptureImage
        tvOutput = binding.tvOutput
        val buttonLoad = binding.btnLoadImage

        button.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                takePicturePreview.launch(null)
            } else {
                requestPermission.launch(android.Manifest.permission.CAMERA)
            }
        }
        // 이미지 타입 설정
        buttonLoad.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                intent.type = "image/*"
                val mimeType = arrayOf("image/jped", "image/png", "image/jpg")
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeType)
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                onresult.launch(intent)
            } else {
                requestPermission.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        
        // 구글 검색
        tvOutput.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${tvOutput.text}"))
            startActivity(intent)
        }

        // 이미지 다운
        imageView.setOnClickListener setOnLongClickListener@{
            requestPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return@setOnLongClickListener
        }
    }

    // 카메라 요청
    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted -> if (granted) {
            takePicturePreview.launch(null)
        } else {
            Toast.makeText(this, "카메라 권한이 필요합니다!!", Toast.LENGTH_SHORT).show()
        }
    }

    // 카메라 실행
    private val takePicturePreview = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) {
        bitmap -> if (bitmap != null) {
            imageView.setImageBitmap(bitmap)
            outputGenerator(bitmap)
        }
    }

    // 갤러리
    private val onresult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        result -> Log.i("TAG", "This is the result: ${result.data} ${result.resultCode}")
        onResultReceived(GALLERY_REQUEST_CODE, result)
    }

    private fun onResultReceived(requestCode: Int, result: ActivityResult?) {
        when (requestCode) {
            GALLERY_REQUEST_CODE -> {
                if (result?.resultCode == Activity.RESULT_OK) {
                    result.data?.data?.let {
                        uri -> Log.i("TAG", "onResultReceived: $uri")
                        val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
                        imageView.setImageBitmap(bitmap)
                        outputGenerator(bitmap)
                    }
                } else {
                    Log.e("TAG", "onActivityResult: error in selecting Image")
                }
            }
        }
    }

    private fun outputGenerator(bitmap: Bitmap) {
        // 텐서플로우 모델
        val birdsModel = BirdsModel.newInstance(this)

        val newBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val tfimage = TensorImage.fromBitmap(newBitmap)

        val outputs = birdsModel.process(tfimage)
            .probabilityAsCategoryList.apply {
                sortByDescending { it.score }
            }
        val highProbabilityOutput = outputs[0]

        tvOutput.text = highProbabilityOutput.label
        Log.i("TAG", "oututGenerator: $highProbabilityOutput")
    }

    // 이미지 다운
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        isGranted: Boolean -> if (isGranted) {
            AlertDialog.Builder(this).setTitle("이미지 다운로드")
                .setMessage("이미지를 다운받으시겠습니까?")
                .setPositiveButton("Yes") {_, _ ->
                    val drawable: BitmapDrawable = imageView.drawable as BitmapDrawable
                    val bitmap = drawable.bitmap
                    downloadImage(bitmap)
                }
                .setNegativeButton("NO") {dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        } else {
            Toast.makeText(this, "다운로드 권한을 허용해주세요", Toast.LENGTH_LONG).show()
        }
    }

    private fun downloadImage(mBitmap: Bitmap):Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Birds_Images" + System.currentTimeMillis()/1000)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        }
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        if (uri != null) {
            contentResolver.insert(uri, contentValues)?.also {
                contentResolver.openOutputStream(it).use {
                    outputStream -> if (!mBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                        throw IOException("이미지 다운 실패")
                    } else {
                    Toast.makeText(applicationContext, "이미지 다운", Toast.LENGTH_LONG).show()
                    }
                }
                return it
            }
        }
        return null
    }
}