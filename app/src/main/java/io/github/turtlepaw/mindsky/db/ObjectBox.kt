package io.github.turtlepaw.mindsky.db

import android.content.Context
import io.github.turtlepaw.mindsky.MyObjectBox
import io.objectbox.BoxStore

object ObjectBox {
    lateinit var store: BoxStore
        private set

    fun init(context: Context): BoxStore {
        store = MyObjectBox.builder()
            .androidContext(context)
            .build()

        return store
    }
}