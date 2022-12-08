package jp.techacademy.kozo.qa_app

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.android.synthetic.main.activity_question_send.*
import java.io.ByteArrayOutputStream

class QuestionSendActivity : AppCompatActivity(), View.OnClickListener {

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 100
        private const val CHOOSER_REQUEST_CODE = 200
    }
    private var mGenre: Int = 0
    private var mPictureUri: Uri? = null

    /**
    渡ってきたジャンルの番号を保持。UIの準備。
    */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_question_send)
        val extras = intent.extras
        mGenre = extras!!.getInt("genre")
        title = getString(R.string.question_send_title)
        sendButton.setOnClickListener(this)
        imageView.setOnClickListener(this)
    }

    /**
    Intent連携で取得した画像をリサイズしてImageViewに設定。
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == CHOOSER_REQUEST_CODE) {
            if (resultCode != Activity.RESULT_OK) {
                if (mPictureUri != null) {
                    contentResolver.delete(mPictureUri!!, null, null)
                    mPictureUri = null
                }
                return
            }

            val uri = if (data == null || data.data == null) mPictureUri else data.data
            val image: Bitmap
            try {
                val contentResolver = contentResolver
                val inputStream = contentResolver.openInputStream(uri!!)
                image = BitmapFactory.decodeStream(inputStream)
                inputStream!!.close()
            } catch (e: Exception) {
                return
            }

            // 取得したBitmapの長辺を500ピクセルにリサイズする
            val imageWidth = image.width
            val imageHeight = image.height
            val scale = Math.min(500.toFloat() / imageWidth, 500.toFloat() / imageHeight) // (1)
            val matrix = Matrix()
            matrix.postScale(scale, scale)
            val resizedImage = Bitmap.createBitmap(image, 0, 0, imageWidth, imageHeight, matrix, true)

            // BitmapをImageViewに設定する
            imageView.setImageBitmap(resizedImage)
            mPictureUri = null
        }
    }

    /**
     * ImageViewとButtonがタップされた時の処理。
     * ImageViewをタップしたときは必要であれば許可を求めるダイアログを表示。
     */
    override fun onClick(v: View) {
        if (v === imageView) {
            // パーミッションの許可状態を確認する
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    // 許可されている
                    showChooser()
                } else {
                    // 許可されていないので許可ダイアログを表示する
                    requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSIONS_REQUEST_CODE)
                    return
                }
            } else {
                showChooser()
            }
        } else if (v === sendButton) {
            // キーボードが出てたら閉じる
            val im = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            im.hideSoftInputFromWindow(v.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS)

            val dataBaseReference = FirebaseDatabase.getInstance().reference
            val genreRef = dataBaseReference.child(ContentsPATH).child(mGenre.toString())
            val data = HashMap<String, String>()

            // UID
            data["uid"] = FirebaseAuth.getInstance().currentUser!!.uid

            // タイトルと本文を取得する
            val title = titleText.text.toString()
            val body = bodyText.text.toString()

            // 質問orタイトルが入力されていない時はエラーを表示するだけ
            if (title.isEmpty()) {
                Snackbar.make(v, getString(R.string.input_title), Snackbar.LENGTH_LONG).show()
                return
            }
            if (body.isEmpty()) {
                Snackbar.make(v, getString(R.string.question_message), Snackbar.LENGTH_LONG).show()
                return
            }

            // Preferenceから名前を取る
            val sp = PreferenceManager.getDefaultSharedPreferences(this)
            val name = sp.getString(NameKEY, "")

            // FirestoreQuestionのインスタンスを作成し、値を詰めていく
            var fireStoreQuestion = FireStoreQuestion()
            fireStoreQuestion.uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            fireStoreQuestion.title = title
            fireStoreQuestion.body = body
            fireStoreQuestion.name = name!!
            fireStoreQuestion.genre = mGenre

            // 添付画像を取得する
            val drawable = imageView.drawable as? BitmapDrawable

            // 添付画像が設定されていれば画像を取り出してBASE64エンコードする
            if (drawable != null) {
                val bitmap = drawable.bitmap
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                val bitmapString = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)

                fireStoreQuestion.image = bitmapString
            }

            // Firestoreにデータをアップロード
            FirebaseFirestore.getInstance()
                .collection(ContentsPATH)
                .document(fireStoreQuestion.id)
                .set(fireStoreQuestion)
                .addOnSuccessListener {
                    progressBar.visibility = View.GONE
                    finish()
                }
                .addOnFailureListener {
                    it.printStackTrace()
                    progressBar.visibility = View.GONE
                    Snackbar.make(findViewById(android.R.id.content),
                        getString(R.string.question_send_error_message), Snackbar.LENGTH_LONG).show()
                }

            progressBar.visibility = View.VISIBLE
        }
    }

    /**
     * 許可を求めるダイアログからの結果を受け取る。
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSIONS_REQUEST_CODE -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // ユーザーが許可したとき
                    showChooser()
                }
                return
            }
        }
    }

    /**
     * Intent連携の選択ダイアログを表示する
     */
    private fun showChooser() {
        // ギャラリーから選択するIntent
        val galleryIntent = Intent(Intent.ACTION_GET_CONTENT)
        galleryIntent.type = "image/*"
        galleryIntent.addCategory(Intent.CATEGORY_OPENABLE)

        // カメラで撮影するIntent
        val filename = System.currentTimeMillis().toString() + ".jpg"
        val values = ContentValues()
        values.put(MediaStore.Images.Media.TITLE, filename)
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        mPictureUri = contentResolver
            .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, mPictureUri)

        // ギャラリー選択のIntentを与えてcreateChooserメソッドを呼ぶ
        val chooserIntent = Intent.createChooser(galleryIntent, getString(R.string.get_image))

        // EXTRA_INITIAL_INTENTSにカメラ撮影のIntentを追加
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
        startActivityForResult(chooserIntent, CHOOSER_REQUEST_CODE)
    }

}