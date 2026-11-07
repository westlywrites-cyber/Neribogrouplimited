package com.westly.neribogroup.data

import kotlin.random.Random

/**
 * Message wording for the customer service simulation (customer emails and support replies).
 * Placeholders: {name} {first} {app} {plan} {amount} {date} {ref} {agent}.
 * All people, companies and numbers here are made up.
 */
object CaseText {

    data class Msg(val subject: String, val body: String)

    val AGENTS = listOf(
        "Amaka O.", "Tunde A.", "Chiamaka N.", "Ibrahim S.", "Folake B.", "Emeka U.",
        "Hauwa M.", "Seyi L.", "Ngozi E.", "Kunle D.", "Blessing I.", "Yusuf T."
    )

    const val SIGNATURE = "Customer Care, Neribo Group Limited"
    const val SUPPORT_EMAIL = "support@neribogroup.ng"

    fun fill(template: String, vars: Map<String, String>): String {
        var out = template
        for ((k, v) in vars) out = out.replace("{$k}", v)
        return out
    }

    private fun <T> List<T>.pick(random: Random): T = this[random.nextInt(size)]

    // ---------------------------------------------------------------------------------------
    // Customer emails: at least 4 per category
    // ---------------------------------------------------------------------------------------

    private val EMAILS: Map<CaseCategory, List<Msg>> = mapOf(
        CaseCategory.BILLING_RENEWAL to listOf(
            Msg(
                "Charged {amount} for a renewal I did not expect",
                "Good day, my name is {name}. On {date} I was charged {amount} for the renewal of my {plan} plan on {app}. I did not receive a reminder before the charge. Please refund me or explain. Regards, {first}."
            ),
            Msg(
                "Renewal charge on {app}",
                "Hello, I noticed {amount} was taken from my account on {date} for {plan} on {app}. I thought I had stopped using the app. Can you tell me why I was charged again? Thank you. {first}."
            ),
            Msg(
                "Please explain the {amount} debit",
                "Good afternoon. I got a debit alert of {amount} on {date} and it says {app}. I only subscribed to {plan} once and I did not agree to pay every month. Kindly look into it. {name}."
            ),
            Msg(
                "Auto renewal complaint",
                "Dear Customer Care, my {plan} subscription on {app} renewed on {date} for {amount} without any message to me. I would like this charge reversed. Please reply as soon as you can. Regards, {first}."
            )
        ),
        CaseCategory.BILLING_UPGRADE to listOf(
            Msg(
                "I paid {amount} but nothing changed",
                "Good day, I upgraded to {plan} on {app} on {date} and {amount} was taken, but I do not feel any difference in the app. Please check my account. Regards, {first}."
            ),
            Msg(
                "Charged for {plan} by mistake",
                "Hello, I only wanted to look at the {plan} page on {date} and I was charged {amount}. I did not mean to upgrade. Please help me with a refund. Thanks, {name}."
            ),
            Msg(
                "Upgrade to {plan} not clear to me",
                "Good afternoon. On {date} I paid {amount} for {plan} on {app}. Nobody told me what I would get for that money. Please explain what is included or refund me. {first}."
            ),
            Msg(
                "Double check my {plan} payment",
                "Dear team, I paid {amount} for {plan} on {app} on {date}. I want to be sure it was charged only once. Kindly confirm and send me a receipt. Regards, {name}."
            )
        ),
        CaseCategory.PLAN_ACCESS to listOf(
            Msg(
                "Charged {amount} but my plan expired",
                "Good day, I paid {amount} for {plan} on {app} but my plan shows as ended since {date}. I cannot use the {plan} features now. Please fix this. Regards, {first}."
            ),
            Msg(
                "I lost my {plan} features",
                "Hello, my {plan} plan on {app} ended on {date} and now I am back on the free plan. I did not cancel anything. Please restore my plan. Thank you, {name}."
            ),
            Msg(
                "{app} moved me to Free",
                "Dear Customer Care, my {plan} access on {app} stopped on {date}. I had paid {amount} and I expected it to continue. Kindly help. {first}."
            ),
            Msg(
                "Cannot access what I paid for",
                "Good afternoon. I have been a {plan} member on {app} but since {date} the paid features are locked. I want my access back or my {amount} returned. {name}."
            )
        ),
        CaseCategory.DATA_PRIVACY to listOf(
            Msg(
                "Please delete my data",
                "Good day, my name is {name}. I no longer use {app} and I want you to delete my personal data, including my email and phone number. Please confirm when it is done. Regards, {first}."
            ),
            Msg(
                "Request to remove my information",
                "Hello, I did not expect {app} to keep my phone number. Please remove my details from your records and tell me what you hold about me. Thank you. {first}."
            ),
            Msg(
                "What personal data does {app} keep?",
                "Dear Customer Care, I would like to know which of my personal details {app} stores and why. If it is not needed, please delete it. {name}."
            ),
            Msg(
                "Delete my account and data",
                "Good afternoon. Please close my {app} account and delete everything connected to my email. I want written confirmation. Regards, {name}."
            )
        ),
        CaseCategory.ADS_CONTENT to listOf(
            Msg(
                "Too many ads in the app",
                "Good day, the adverts in {app} are too many and they cover the screen. It is hard to use the app. Please reduce them. Regards, {first}."
            ),
            Msg(
                "Inappropriate advert shown in {app}",
                "Hello, I saw an advert in {app} that I think is not suitable for all ages. Please check the ads you allow. Thank you, {name}."
            ),
            Msg(
                "Ads keep interrupting me",
                "Dear team, adverts pop up while I am trying to use {app} and it wastes my data. Is there a way to reduce them? {first}."
            ),
            Msg(
                "Misleading ad on {app}",
                "Good afternoon. An advert inside {app} promised something that was not true when I clicked it. Please look into the ads in your app. Regards, {name}."
            )
        )
    )

    fun customerEmail(category: CaseCategory, random: Random): Msg =
        EMAILS.getValue(category).pick(random)

    // ---------------------------------------------------------------------------------------
    // Support replies: acknowledgement, request for information, resolution (3+ per category)
    // ---------------------------------------------------------------------------------------

    private val ACKS = listOf(
        "Dear {name}, thank you for contacting Neribo Group. We have received your message about {app} and opened case {ref}. A member of our team is looking into it and will get back to you shortly.\n\n{agent}\n$SIGNATURE",
        "Hello {first}, we are sorry for the trouble. Your complaint is now case {ref}. Please keep this reference. We will review your account on {app} and reply with an update soon.\n\n{agent}\n$SIGNATURE",
        "Good day {first}, thanks for writing to us. We have logged your issue as {ref} and we are checking the details on {app}. We will reach you again in a few days.\n\n{agent}\n$SIGNATURE"
    )

    fun acknowledgement(random: Random): String = ACKS.pick(random)

    private val INFO: Map<CaseCategory, List<String>> = mapOf(
        CaseCategory.BILLING_RENEWAL to listOf(
            "Dear {first}, to check the charge on {date}, please send us the Google Play receipt or a screenshot of the debit alert. Case {ref}.\n\n{agent}\n$SIGNATURE",
            "Hello {first}, please reply with your Google Play order number for the {amount} charge so we can match it on our side. Case {ref}.\n\n{agent}\n$SIGNATURE",
            "Good day {first}, we need a screenshot of the renewal message from Google Play, if you received one, to continue with case {ref}.\n\n{agent}\n$SIGNATURE"
        ),
        CaseCategory.BILLING_UPGRADE to listOf(
            "Dear {first}, please send the receipt or screenshot of the {amount} payment for {plan} so we can confirm it. Case {ref}.\n\n{agent}\n$SIGNATURE",
            "Hello {first}, could you tell us the email you used on {app} and send the payment screenshot? Case {ref}.\n\n{agent}\n$SIGNATURE",
            "Good day {first}, to review the upgrade of {date}, please share the Google Play order number. Case {ref}.\n\n{agent}\n$SIGNATURE"
        ),
        CaseCategory.PLAN_ACCESS to listOf(
            "Dear {first}, please send a screenshot of what you see on the plan page in {app}, and the date you last paid. Case {ref}.\n\n{agent}\n$SIGNATURE",
            "Hello {first}, we need your Google Play receipt to confirm the last payment for {plan}. Case {ref}.\n\n{agent}\n$SIGNATURE",
            "Good day {first}, kindly confirm the email on your {app} account and send a screenshot of the locked feature. Case {ref}.\n\n{agent}\n$SIGNATURE"
        ),
        CaseCategory.DATA_PRIVACY to listOf(
            "Dear {first}, to protect your privacy we must confirm it is you. Please reply from the email on your {app} account and state the phone number on it. Case {ref}.\n\n{agent}\n$SIGNATURE",
            "Hello {first}, please confirm which details you want removed (email, phone number or both). Case {ref}.\n\n{agent}\n$SIGNATURE",
            "Good day {first}, for your safety, kindly confirm your registered email and the date you joined {app}. Case {ref}.\n\n{agent}\n$SIGNATURE"
        ),
        CaseCategory.ADS_CONTENT to listOf(
            "Dear {first}, please send a screenshot of the advert and tell us which screen of {app} you were on. Case {ref}.\n\n{agent}\n$SIGNATURE",
            "Hello {first}, could you tell us your phone model and the time you saw the advert? Case {ref}.\n\n{agent}\n$SIGNATURE",
            "Good day {first}, kindly share a screenshot of the advert so we can report it to our ad partner. Case {ref}.\n\n{agent}\n$SIGNATURE"
        )
    )

    fun infoRequest(category: CaseCategory, random: Random): String = INFO.getValue(category).pick(random)

    /** A resolution: a short title and the body. No company money is paid for normal complaints. */
    private val RESOLUTIONS: Map<CaseCategory, List<Msg>> = mapOf(
        CaseCategory.BILLING_RENEWAL to listOf(
            Msg(
                "Your renewal explained",
                "Dear {first}, we checked case {ref}. The {amount} charge on {date} was the normal renewal of your {plan} plan on {app}. Renewals go through Google Play. You can stop future renewals in the Google Play subscriptions page. We have noted your account.\n\n{agent}\n$SIGNATURE"
            ),
            Msg(
                "Refund requested through Google Play",
                "Hello {first}, we have asked Google Play to refund the {amount} charged on {date}. Google Play handles the refund and it can take a few working days to reach you. Case {ref} is now resolved.\n\n{agent}\n$SIGNATURE"
            ),
            Msg(
                "Renewal cancelled for you",
                "Good day {first}, we have switched off the renewal of your {plan} plan so you will not be charged again. The refund of {amount} goes through Google Play. Case {ref} is resolved.\n\n{agent}\n$SIGNATURE"
            )
        ),
        CaseCategory.BILLING_UPGRADE to listOf(
            Msg(
                "Your upgrade confirmed",
                "Dear {first}, we confirmed that {amount} was charged once on {date} for {plan}. Your {plan} features are active on {app}. Please restart the app if you do not see them. Case {ref} is resolved.\n\n{agent}\n$SIGNATURE"
            ),
            Msg(
                "Refund requested through Google Play",
                "Hello {first}, since the upgrade was not intended, we have asked Google Play to refund the {amount} from {date}. Google Play processes it and it may take a few working days. Case {ref} is resolved.\n\n{agent}\n$SIGNATURE"
            ),
            Msg(
                "What {plan} includes",
                "Good day {first}, we have sent you the list of what {plan} includes on {app}. Your payment of {amount} on {date} was charged once and is correct. Case {ref} is resolved.\n\n{agent}\n$SIGNATURE"
            )
        ),
        CaseCategory.PLAN_ACCESS to listOf(
            Msg(
                "Your plan has been restored",
                "Dear {first}, we found the problem on case {ref} and restored your {plan} access on {app}. Please close and open the app again. We are sorry for the inconvenience.\n\n{agent}\n$SIGNATURE"
            ),
            Msg(
                "Why your plan ended",
                "Hello {first}, your {plan} plan ended on {date} because the subscription period finished and it was not renewed. You can subscribe again at any time. Case {ref} is resolved.\n\n{agent}\n$SIGNATURE"
            ),
            Msg(
                "Access fixed",
                "Good day {first}, the locked features on {app} were a display problem on our side. It is fixed now. Thank you for your patience. Case {ref} is resolved.\n\n{agent}\n$SIGNATURE"
            )
        ),
        CaseCategory.DATA_PRIVACY to listOf(
            Msg(
                "Your personal data has been deleted",
                "Dear {first}, as you asked in case {ref}, we have deleted your personal data from {app}. We keep only what the law requires us to keep. Thank you for your patience.\n\n{agent}\n$SIGNATURE"
            ),
            Msg(
                "Your data request is complete",
                "Hello {first}, we have removed your email and phone number from {app} and closed your account. You will not receive further messages from us. Case {ref} is resolved.\n\n{agent}\n$SIGNATURE"
            ),
            Msg(
                "What we keep and why",
                "Good day {first}, we sent you a summary of the details {app} holds (name, email and plan). As you requested, the extra details have been deleted. Case {ref} is resolved.\n\n{agent}\n$SIGNATURE"
            )
        ),
        CaseCategory.ADS_CONTENT to listOf(
            Msg(
                "Ad settings corrected",
                "Dear {first}, thank you for the report on case {ref}. We have reduced how often adverts show in {app}. We are sorry for the interruption.\n\n{agent}\n$SIGNATURE"
            ),
            Msg(
                "Advert reported and blocked",
                "Hello {first}, we reported the advert you saw to our ad partner and asked for it to be blocked in {app}. Case {ref} is resolved. Thank you for helping us.\n\n{agent}\n$SIGNATURE"
            ),
            Msg(
                "Ads explained",
                "Good day {first}, adverts help keep the free plan of {app} available. We have changed our settings so fewer adverts show on each screen. Case {ref} is resolved.\n\n{agent}\n$SIGNATURE"
            )
        )
    )

    fun resolution(category: CaseCategory, random: Random): Msg = RESOLUTIONS.getValue(category).pick(random)

    // ---------------------------------------------------------------------------------------
    // Customer replies and company messages
    // ---------------------------------------------------------------------------------------

    private val THANKS = listOf(
        "Thank you very much for your help, I am satisfied. Regards, {first}.",
        "Good day, I have seen your reply and it is fine now. Thanks to the team. {first}.",
        "Thank you for sorting this out so quickly. God bless. {first}.",
        "Received, thank you. I appreciate the follow up. Regards, {name}."
    )

    fun thanks(random: Random): String = THANKS.pick(random)

    private val DETAILS = listOf(
        "Good day, please find the details you asked for. I have attached the screenshot and the order number is in it. Regards, {first}.",
        "Hello, I am sending the receipt as requested. Kindly look at it and update me. Thanks, {first}.",
        "Dear Customer Care, here is the information. I hope this helps you to solve my problem. {name}."
    )

    fun details(random: Random): String = DETAILS.pick(random)

    private val CLOSING_NO_REPLY = listOf(
        "Dear {first}, we have not heard back from you about case {ref}, so we are closing it for now. If you still need help, reply to this email and we will open it again.\n\n{agent}\n$SIGNATURE",
        "Hello {first}, case {ref} is closed because we did not hear back after our request for information. You are welcome to write to us again anytime.\n\n{agent}\n$SIGNATURE"
    )

    fun closedNoReply(random: Random): String = CLOSING_NO_REPLY.pick(random)

    private val REJECTION = listOf(
        "I have read your offer but it does not solve my problem. I am not satisfied and I want a better answer. {first}.",
        "Thank you, but I do not accept this solution. I expected a full refund. {first}.",
        "This reply is not acceptable to me. I have been waiting for days. Please escalate. {name}."
    )

    fun rejection(random: Random): String = REJECTION.pick(random)

    private val OFFER = listOf(
        "Dear {first}, after reviewing case {ref} we can offer an explanation and a refund request through Google Play for the amount in question. Please tell us if this is acceptable.\n\n{agent}\n$SIGNATURE",
        "Hello {first}, our proposal for case {ref} is to correct your account and ask Google Play to review the charge. We hope this resolves it.\n\n{agent}\n$SIGNATURE"
    )

    fun offer(random: Random): String = OFFER.pick(random)

    private val ESCALATION = listOf(
        "Good day. I am not happy with how my complaint ({ref}) was handled. I will report {app} to the {regulator} if I do not get a proper solution. {name}.",
        "Dear Neribo Group, this is my final message on case {ref}. I am taking the matter to the {regulator}. Regards, {name}.",
        "Hello, since you have not solved my problem, I have decided to write to the {regulator} about {app}. Case {ref}. {first}.",
        "To whom it may concern: I will report this matter ({ref}) to the {regulator} for their action. {name}."
    )

    fun escalation(random: Random): String = ESCALATION.pick(random)
}
