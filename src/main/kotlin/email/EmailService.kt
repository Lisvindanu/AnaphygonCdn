package org.anaphygon.email

import org.anaphygon.config.SecureConfig
import org.anaphygon.util.Logger
import java.util.Properties
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.Authenticator
import javax.mail.PasswordAuthentication

class EmailService {
    private val logger = Logger("EmailService")
    private val session: Session

    init {
        val props = Properties()
        props["mail.smtp.host"] = SecureConfig.emailHost
        props["mail.smtp.port"] = SecureConfig.emailPort
        props["mail.smtp.auth"] = "true"
        
        // Use SSL instead of STARTTLS for port 465
        props["mail.smtp.socketFactory.port"] = SecureConfig.emailPort
        props["mail.smtp.socketFactory.class"] = "javax.net.ssl.SSLSocketFactory"
        props["mail.smtp.ssl.enable"] = "true"
        props["mail.smtp.ssl.trust"] = SecureConfig.emailHost
        
        // Enable debug for troubleshooting
        props["mail.debug"] = "true"
        
        // Log all credentials being used (except password)
        logger.info("Initializing email service with:")
        logger.info("- Host: ${SecureConfig.emailHost}")
        logger.info("- Port: ${SecureConfig.emailPort}")
        logger.info("- Username: ${SecureConfig.emailUsername}")
        logger.info("- Password length: ${SecureConfig.emailPassword.length}")
        logger.info("- SSL enabled: true")

        session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(SecureConfig.emailUsername, SecureConfig.emailPassword)
            }
        })
    }

    fun sendVerificationEmail(email: String, username: String, token: String) {
        try {
            logger.info("Preparing to send verification email to $email")
            
            val message = MimeMessage(session)
            message.setFrom(InternetAddress(SecureConfig.emailSender))
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email))
            message.subject = "Please verify your email for Anaphygon CDN"

            val verificationLink = "${SecureConfig.appBaseUrl}/verify?token=$token"

            val htmlContent = """
                <html>
                <head>
                    <style>
                        body { font-family: Arial, sans-serif; color: #333; line-height: 1.6; }
                        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                        .header { background-color: #4361ee; color: white; padding: 20px; text-align: center; }
                        .content { padding: 20px; background-color: #f8f9fa; }
                        .button { display: inline-block; background-color: #4361ee; color: white; text-decoration: none; padding: 10px 20px; border-radius: 5px; margin: 20px 0; }
                        .footer { text-align: center; margin-top: 20px; font-size: 12px; color: #777; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>Anaphygon CDN</h1>
                        </div>
                        <div class="content">
                            <p>Hello $username,</p>
                            <p>Thank you for registering with Anaphygon CDN. Please verify your email by clicking the button below:</p>
                            <p style="text-align: center;">
                                <a href="$verificationLink" class="button">Verify Email</a>
                            </p>
                            <p>Or copy and paste this link into your browser:</p>
                            <p>$verificationLink</p>
                            <p>This link will expire in 24 hours.</p>
                            <p>If you did not register for an account, please ignore this email.</p>
                        </div>
                        <div class="footer">
                            <p>&copy; 2025 Anaphygon CDN. All rights reserved.</p>
                        </div>
                    </div>
                </body>
                </html>
            """.trimIndent()

            message.setContent(htmlContent, "text/html; charset=utf-8")

            logger.info("Sending email via SMTP...")
            
            // Try alternative approach - create a new Transport and connect explicitly
            try {
                val transport = session.getTransport("smtp")
                transport.connect(
                    SecureConfig.emailHost, 
                    SecureConfig.emailPort.toInt(), 
                    SecureConfig.emailUsername, 
                    SecureConfig.emailPassword
                )
                transport.sendMessage(message, message.allRecipients)
                transport.close()
                logger.info("Email sent successfully using explicit transport")
            } catch (e: Exception) {
                logger.error("Explicit transport failed, trying default Transport.send(): ${e.message}")
                // Fall back to the standard approach if the explicit one fails
                Transport.send(message)
            }
            
            logger.info("Verification email sent successfully to $email")
        } catch (e: MessagingException) {
            logger.error("Failed to send verification email to $email: ${e.message}", e)
            e.printStackTrace() // Print full stack trace for debugging
            throw e
        } catch (e: Exception) {
            logger.error("Unexpected error sending email: ${e.message}", e)
            e.printStackTrace()
            throw e
        }
    }

    fun sendPasswordResetEmail(email: String, username: String, token: String) {
        try {
            logger.info("Preparing to send password reset email to $email")

            val message = MimeMessage(session)
            message.setFrom(InternetAddress(SecureConfig.emailSender))
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email))
            message.subject = "Password Reset Request for Anaphygon CDN"

            val resetLink = "${SecureConfig.appBaseUrl}/reset-password?token=$token"

            val htmlContent = """
                <html>
                <head>
                    <style>
                        body { font-family: Arial, sans-serif; color: #333; line-height: 1.6; }
                        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                        .header { background-color: #4361ee; color: white; padding: 20px; text-align: center; }
                        .content { padding: 20px; background-color: #f8f9fa; }
                        .button { display: inline-block; background-color: #4361ee; color: white; text-decoration: none; padding: 10px 20px; border-radius: 5px; margin: 20px 0; }
                        .footer { text-align: center; margin-top: 20px; font-size: 12px; color: #777; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>Anaphygon CDN</h1>
                        </div>
                        <div class="content">
                            <p>Hello $username,</p>
                            <p>We received a request to reset your password for Anaphygon CDN. Please click the button below to reset your password:</p>
                            <p style="text-align: center;">
                                <a href="$resetLink" class="button">Reset Password</a>
                            </p>
                            <p>Or copy and paste this link into your browser:</p>
                            <p>$resetLink</p>
                            <p>This link will expire in 1 hour.</p>
                            <p>If you did not request a password reset, please ignore this email.</p>
                        </div>
                        <div class="footer">
                            <p>&copy; 2025 Anaphygon CDN. All rights reserved.</p>
                        </div>
                    </div>
                </body>
                </html>
            """.trimIndent()

            message.setContent(htmlContent, "text/html; charset=utf-8")

            // Try alternative approach - create a new Transport and connect explicitly
            try {
                val transport = session.getTransport("smtp")
                transport.connect(
                    SecureConfig.emailHost, 
                    SecureConfig.emailPort.toInt(), 
                    SecureConfig.emailUsername, 
                    SecureConfig.emailPassword
                )
                transport.sendMessage(message, message.allRecipients)
                transport.close()
                logger.info("Password reset email sent successfully using explicit transport")
            } catch (e: Exception) {
                logger.error("Explicit transport failed, trying default Transport.send(): ${e.message}")
                // Fall back to the standard approach if the explicit one fails
                Transport.send(message)
                logger.info("Password reset email sent successfully using Transport.send()")
            }
        } catch (e: MessagingException) {
            logger.error("Failed to send password reset email to $email: ${e.message}", e)
            e.printStackTrace()
            throw e
        }
    }
}