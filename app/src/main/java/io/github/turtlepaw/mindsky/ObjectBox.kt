package io.github.turtlepaw.mindsky

import android.content.Context
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
