package ir.jrweb.banksms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import java.text.DecimalFormat
import java.util.regex.Pattern

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].originatingAddress ?: ""
        val fullBody = StringBuilder()
        for (msg in messages) {
            fullBody.append(msg.messageBody)
        }

        val bodyText = normalizePersian(fullBody.toString())
        val normalizedSender = normalizePersian(sender)

        if (!isBankSms(normalizedSender, bodyText)) return

        val txType = detectTransactionType(bodyText) ?: return
        val amountToman = extractAmountInToman(bodyText) ?: return

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)) {
            val serviceIntent = Intent(context, PopupService::class.java).apply {
                putExtra("TYPE", txType)
                putExtra("AMOUNT", amountToman)
                putExtra("SENDER", sender)
            }
            context.startService(serviceIntent)
        }
    }

    private fun normalizePersian(input: String): String {
        return input
            .replace("ي", "ی")
            .replace("ك", "ک")
            .replace("۰", "0").replace("۱", "1").replace("۲", "2")
            .replace("۳", "3").replace("۴", "4").replace("۵", "5")
            .replace("۶", "6").replace("۷", "7").replace("۸", "8")
            .replace("۹", "9")
    }

    private fun isBankSms(sender: String, body: String): Boolean {
        val bankKeywords = listOf(
            "بانک", "موجودی", "مانده", "حساب", "کارت", "واریز", "برداشت",
            "پرید", "نشست", "ساتنا", "پایا", "شبا", "تراکنش", "ریال", "تومان",
            "ملت", "ملی", "صادرات", "تجارت", "سپه", "کشاورزی", "مسکن",
            "سامان", "پاسارگاد", "پارسیان", "اقتصاد نوین", "شهر", "دی",
            "آینده", "سینا", "رفاه", "گردشگری", "ایران زمین", "پست بانک",
            "بلو", "blu", "ویپاد", "رسالت", "مهر ایران"
        )
        val hasBankWord = bankKeywords.any { body.contains(it, ignoreCase = true) || sender.contains(it, ignoreCase = true) }
        val isServiceNumber = sender.startsWith("98") || (!sender.startsWith("+989") && sender.length in 4..14)
        return hasBankWord && isServiceNumber
    }

    private fun detectTransactionType(body: String): String? {
        val depositKeywords = listOf(
            "واریز", "افزایش", "بستانکار", "انتقال به", "دریافت وجه",
            "واریزی", "به حسابت نشست", "نشست به حسابت", "به حساب شما نشست"
        )
        val withdrawKeywords = listOf(
            "برداشت", "کسر", "بدهکار", "خرید", "انتقال از",
            "پرداخت قبض", "کارمزد", "پرید", "از حسابت پرید", "از حساب شما پرید"
        )

        for (kw in depositKeywords) {
            if (body.contains(kw)) return "DEPOSIT"
        }
        for (kw in withdrawKeywords) {
            if (body.contains(kw)) return "WITHDRAW"
        }

        if (Pattern.compile("\\+\\s*[0-9,]{4,}").matcher(body).find() ||
            Pattern.compile("[0-9,]{4,}\\s*\\+").matcher(body).find()) {
            return "DEPOSIT"
        }
        if (Pattern.compile("-\\s*[0-9,]{4,}").matcher(body).find() ||
            Pattern.compile("[0-9,]{4,}\\s*-").matcher(body).find()) {
            return "WITHDRAW"
        }

        return null
    }

    private fun extractAmountInToman(body: String): String? {
        val cleaned = body
            .replace(Regex("\\b\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}\\b"), "")
            .replace(Regex("(موجودی|مانده)\\s*:?\\s*[0-9,\\.]+"), "")

        val matcher = Pattern.compile("(?:مبلغ|واریز|برداشت|کسر|افزایش)?\\s*:?\\s*([0-9][0-9,\\.]{2,})").matcher(cleaned)
        var rawNumberStr: String? = null

        while (matcher.find()) {
            val candidate = matcher.group(1)?.replace(",", "")?.replace(".", "") ?: continue
            if (candidate.length in 3..13) {
                rawNumberStr = candidate
                break
            }
        }

        val amount = rawNumberStr?.toLongOrNull() ?: return null
        if (amount <= 0) return null

        val isExplicitToman = body.contains("تومان") && !body.contains("ریال")
        val tomanValue = if (isExplicitToman) amount else amount / 10

        val formatter = DecimalFormat("#,###")
        return "${formatter.format(tomanValue)} تومان"
    }
}