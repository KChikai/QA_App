package jp.techacademy.kozo.qa_app

import java.util.*

class FireStoreQuestion {
    var id = UUID.randomUUID().toString()
    var title = ""
    var body = ""
    var name = ""
    var uid = ""
    var image = ""
    var genre = 0
    var answers: ArrayList<Answer> = arrayListOf()
}