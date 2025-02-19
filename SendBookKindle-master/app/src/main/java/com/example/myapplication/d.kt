package com.example.myapplication

import android.os.Build
import androidx.annotation.RequiresApi
import java.util.Properties
import javax.activation.DataHandler
import javax.activation.DataSource
import javax.activation.FileDataSource
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

object EmailSender {

    @RequiresApi(Build.VERSION_CODES.O)
    fun sendEmailWithAttachment(
        to: String,
        subject: String,
        bodyText: String,
        attachmentPath: String,
        from: String,
        host: String,
        port: String,
        username: String,
        password: String,
        oldFilePath:String
    ) {
        val properties = Properties().apply {
            put("mail.smtp.host", host)
            put("mail.smtp.port", port)
            put("mail.smtp.auth", "true")
            put("mail.smtp.socketFactory.port", port)
            put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
        }

        val session = Session.getInstance(properties, object : javax.mail.Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(username, password)
            }
        })

        try {
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(from))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                setSubject(subject)

                val mimeBodyPart = MimeBodyPart().apply {
                    setText(bodyText)
                }

                val attachmentBodyPart = MimeBodyPart().apply {
                    val source: DataSource = FileDataSource(attachmentPath)
                    dataHandler = DataHandler(source)
                    fileName = source.name
                }

                val multipart: Multipart = MimeMultipart().apply {
                    addBodyPart(mimeBodyPart)
                    addBodyPart(attachmentBodyPart)
                }

                setContent(multipart)
            }

            Thread {
                Transport.send(message)
                println("Email sent successfully.")

                renameFile(attachmentPath, oldFilePath).toString()

            }.start()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}