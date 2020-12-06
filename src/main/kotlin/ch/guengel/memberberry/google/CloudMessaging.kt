package ch.guengel.memberberry.google

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import javax.inject.Singleton

@Singleton
class CloudMessaging {
    private val firebaseApp = FirebaseApp.initializeApp(
        FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.getApplicationDefault())
            .build()

    )

    fun sendMessage(fcmToken: String, body: String) {
        val notification = Notification
            .builder()
            .setTitle("'member?")
            .setBody(body)
            .build()
        val message = Message.builder()
            .setNotification(notification)
            .setToken(fcmToken)
            .build()

        FirebaseMessaging.getInstance(firebaseApp).send(message)
    }
}