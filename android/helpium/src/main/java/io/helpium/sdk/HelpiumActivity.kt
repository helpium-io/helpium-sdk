package io.helpium.sdk

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.io.ByteArrayOutputStream

/** The chat: home, thread, pre-chat form and rating. Built in code so the SDK needs no androidx. */
class HelpiumActivity : Activity() {
    private lateinit var store: ChatStore
    private lateinit var content: FrameLayout
    private lateinit var titleView: TextView
    private lateinit var backButton: ImageButton
    private lateinit var moreButton: ImageButton
    private var thread: ThreadUi? = null
    private val listener: () -> Unit = { render() }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Strings.localized(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        when (Helpium.store?.appearance?.colorScheme) {
            HelpiumColorScheme.LIGHT -> setTheme(R.style.Helpium_Theme_Light)
            HelpiumColorScheme.DARK -> setTheme(R.style.Helpium_Theme_Dark)
            else -> Unit
        }
        super.onCreate(savedInstanceState)
        val current = Helpium.store
        if (current == null) {
            finish()
            return
        }
        store = current

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        palette = resolvePalette()
        bar = topBar()
        root.addView(bar)
        headerDivider = divider()
        root.addView(headerDivider)
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        store.addListener(listener)
        store.start()
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized) store.isVisible = true
    }

    override fun onPause() {
        super.onPause()
        if (::store.isInitialized) store.isVisible = false
    }

    override fun onDestroy() {
        if (::store.isInitialized) store.removeListener(listener)
        super.onDestroy()
    }

    @Deprecated("Activity API kept for minSdk 21")
    override fun onBackPressed() {
        if (store.screen == ChatStore.Screen.THREAD) store.goHome() else super.onBackPressed()
    }

    // region Chrome

    private lateinit var palette: Palette
    private lateinit var bar: LinearLayout
    private lateinit var headerDivider: View
    private lateinit var closeButton: ImageButton

    private val accent get() = palette.primary
    private val onAccent get() = palette.onPrimary

    private fun resolvePalette(): Palette {
        val dark = when (store.appearance.colorScheme) {
            HelpiumColorScheme.LIGHT -> false
            HelpiumColorScheme.DARK -> true
            HelpiumColorScheme.SYSTEM -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        return Palette.resolve(store.appearance, store.settings?.primaryColor, dark)
    }

    /** Window, status bar and top bar in the header colors. */
    @Suppress("DEPRECATION")
    private fun applyChrome() {
        window.decorView.setBackgroundColor(palette.background)
        window.statusBarColor = palette.headerBackground
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            val flags = window.decorView.systemUiVisibility
            window.decorView.systemUiVisibility = if (palette.headerIsDark) {
                flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            } else {
                flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
        bar.setBackgroundColor(palette.headerBackground)
        headerDivider.setBackgroundColor(palette.border)
        titleView.setTextColor(palette.headerText)
        listOf(backButton, moreButton, closeButton).forEach { it.imageTintList = ColorStateList.valueOf(palette.headerText) }
    }

    private fun topBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        backButton = iconButton(android.R.drawable.ic_media_previous, R.string.helpium_back) { store.goHome() }
        backButton.setImageResource(R.drawable.helpium_ic_back)
        titleView = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), 0, dp(8), 0)
            maxLines = 1
        }
        moreButton = iconButton(R.drawable.helpium_ic_more, R.string.helpium_more) { confirmClose() }
        closeButton = iconButton(R.drawable.helpium_ic_close, R.string.helpium_close) { finish() }
        bar.addView(backButton)
        bar.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(moreButton)
        bar.addView(closeButton)
        return bar
    }

    private fun render() {
        val fresh = resolvePalette()
        if (fresh != palette) {
            // New colors: rebuild every view with them.
            palette = fresh
            thread = null
            content.removeAllViews()
        }
        applyChrome()

        val screen = store.screen
        backButton.visibility = if (screen == ChatStore.Screen.THREAD) View.VISIBLE else View.GONE
        moreButton.visibility = if (screen == ChatStore.Screen.THREAD && store.hasConversation && !store.isClosed) View.VISIBLE else View.GONE
        titleView.text = when {
            screen != ChatStore.Screen.THREAD -> getString(R.string.helpium_title)
            store.agent != null -> getString(R.string.helpium_chatting_with, store.agent!!.name)
            store.handoffRequested && !store.isClosed -> getString(R.string.helpium_waiting_for_agent)
            else -> store.settings?.botName ?: getString(R.string.helpium_title)
        }

        store.notice?.let {
            store.notice = null
            AlertDialog.Builder(this).setMessage(it).setPositiveButton(android.R.string.ok, null).show()
        }

        if (screen == ChatStore.Screen.THREAD) {
            val ui = thread ?: ThreadUi().also { thread = it; content.removeAllViews(); content.addView(it.root) }
            ui.render()
            return
        }

        thread = null
        content.removeAllViews()
        content.addView(when (screen) {
            ChatStore.Screen.LOADING -> centered(ProgressBar(this))
            ChatStore.Screen.FAILED -> centered(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(secondaryText(getString(R.string.helpium_loading_failed)))
                addView(textButton(getString(R.string.helpium_retry)) { store.reload() })
            })
            ChatStore.Screen.UNAVAILABLE -> centered(secondaryText(getString(R.string.helpium_offline)))
            else -> home()
        })
    }

    private fun home(): View {
        val settings = store.settings
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        column.addView(TextView(this).apply {
            text = getString(R.string.helpium_welcome_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(palette.text)
        })
        column.addView(secondaryText(getString(R.string.helpium_welcome_subtitle)).apply { setPadding(0, dp(6), 0, 0) })
        if (settings?.isOnline == false) {
            column.addView(secondaryText(settings.offlineMessage ?: getString(R.string.helpium_offline)).apply { setPadding(0, dp(8), 0, 0) })
        }

        val label = getString(if (store.hasConversation) R.string.helpium_continue_conversation else R.string.helpium_new_conversation)
        val start = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(18), 0)
            background = RippleDrawable(ColorStateList.valueOf(0x33000000), rounded(palette.primary, dp(14).toFloat()), null)
            isClickable = true
            setOnClickListener { if (store.hasConversation) store.openThread() else store.startNewConversation() }
            addView(TextView(this@HelpiumActivity).apply {
                text = label
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(palette.onPrimary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (store.unreadCount > 0) {
                addView(TextView(this@HelpiumActivity).apply {
                    text = store.unreadCount.toString()
                    typeface = Typeface.DEFAULT_BOLD
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(palette.unreadBadgeText)
                    setPadding(dp(7), dp(2), dp(7), dp(2))
                    background = rounded(palette.unreadBadge, dp(10).toFloat())
                })
            }
        }
        column.addView(start, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(24) })

        if (!store.hasConversation) {
            settings?.instantAnswers?.forEach { answer ->
                column.addView(outlinedRow(answer.label) {
                    if (answer.isShortcut && store.needsPrechat) store.startNewConversation() else store.sendInstantAnswer(answer)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
            }
        }
        return ScrollView(this).apply { addView(column) }
    }

    private fun confirmClose() {
        AlertDialog.Builder(this)
            .setTitle(R.string.helpium_close_confirm_title)
            .setMessage(R.string.helpium_close_confirm_message)
            .setPositiveButton(R.string.helpium_end) { _, _ -> store.close() }
            .setNegativeButton(R.string.helpium_cancel, null)
            .show()
    }

    // endregion

    // region Thread

    private inner class ThreadUi {
        val root = LinearLayout(this@HelpiumActivity).apply { orientation = LinearLayout.VERTICAL }
        private val adapter = MessagesAdapter()
        private val list = ListView(this@HelpiumActivity).apply {
            divider = null
            dividerHeight = 0
            selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            clipToPadding = false
            setPadding(dp(12), dp(8), dp(12), dp(8))
            transcriptMode = ListView.TRANSCRIPT_MODE_NORMAL
            isStackFromBottom = true
            this.adapter = this@ThreadUi.adapter
        }
        private val footer = FrameLayout(this@HelpiumActivity)
        private var input: EditText? = null
        private var footerState = ""
        private var lastCount = 0

        init {
            root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            root.addView(divider())
            root.addView(footer)
        }

        fun render() {
            adapter.items = buildItems()
            adapter.notifyDataSetChanged()
            if (adapter.items.size != lastCount) {
                lastCount = adapter.items.size
                list.post { list.setSelection(adapter.count - 1) }
            }

            val state = when {
                store.showRating -> "rating"
                store.isClosed -> "closed:${store.closedReason}"
                else -> "composer"
            }
            if (state != footerState) {
                footerState = state
                footer.removeAllViews()
                footer.addView(when (state) {
                    "rating" -> rating()
                    "composer" -> composer()
                    else -> closed()
                })
            }
        }

        private fun buildItems(): List<Item> {
            val items = store.messages.map { Item.Message(it) }.toMutableList<Item>()
            if (store.showHandoffButton) items += Item.Handoff
            if (store.isBotTyping || store.isAgentTyping) items += Item.Typing
            return items
        }

        private fun composer(): View {
            val row = LinearLayout(this@HelpiumActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            val attach = iconButton(R.drawable.helpium_ic_attach, R.string.helpium_attach_image) {
                if (!store.needsPrechat) pickImage()
            }.apply { imageTintList = ColorStateList.valueOf(palette.textSecondary) }
            val field = EditText(this@HelpiumActivity).apply {
                hint = getString(R.string.helpium_placeholder)
                background = rounded(palette.inputBackground, dp(20).toFloat())
                setPadding(dp(14), dp(10), dp(14), dp(10))
                setTextColor(palette.inputText)
                setHintTextColor(palette.textSecondary)
                maxLines = 5
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                imeOptions = EditorInfo.IME_ACTION_SEND
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) { if (!s.isNullOrEmpty()) store.customerTyping() }
                })
                setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_SEND) { send(); true } else false }
            }
            input = field
            val sendButton = iconButton(R.drawable.helpium_ic_send, R.string.helpium_send) { send() }.apply {
                imageTintList = ColorStateList.valueOf(accent)
            }
            row.addView(attach)
            row.addView(field, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(sendButton)
            return row
        }

        private fun send() {
            val field = input ?: return
            val text = field.text.toString().trim()
            if (text.isEmpty() || store.isBotTyping) return
            field.setText("")
            if (store.needsPrechat) prechat(text) else store.send(text)
        }

        private fun closed(): View = LinearLayout(this@HelpiumActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            addView(secondaryText(getString(when (store.closedReason) {
                "customer" -> R.string.helpium_closed_customer
                "inactivity" -> R.string.helpium_closed_inactivity
                "agent", "rule", "unrelated", "automated" -> R.string.helpium_closed_team
                else -> R.string.helpium_closed
            })).apply { gravity = Gravity.CENTER })
            addView(primaryButton(getString(R.string.helpium_start_new)) { store.startNewConversation() },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(10) })
        }

        private fun rating(): View {
            val column = LinearLayout(this@HelpiumActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
            }
            column.addView(TextView(this@HelpiumActivity).apply {
                text = getString(R.string.helpium_rate_title)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(palette.text)
            })
            val faces = listOf("😞", "😐", "🙂", "😀", "🤩")
            val labels = listOf(R.string.helpium_rating_1, R.string.helpium_rating_2, R.string.helpium_rating_3, R.string.helpium_rating_4, R.string.helpium_rating_5)
            val comment = EditText(this@HelpiumActivity).apply {
                hint = getString(R.string.helpium_rate_comment)
                visibility = View.GONE
            }
            var selected = 0
            val send = textButton(getString(R.string.helpium_rate_send)) { store.rate(selected, comment.text.toString()) }.apply { visibility = View.GONE }
            val row = LinearLayout(this@HelpiumActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            faces.forEachIndexed { index, face ->
                val rating = index + 1
                row.addView(LinearLayout(this@HelpiumActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    addView(TextView(this@HelpiumActivity).apply { text = face; setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f) })
                    addView(secondaryText(getString(labels[index])).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f) })
                    setOnClickListener {
                        if (rating >= 3) {
                            store.rate(rating, null)
                        } else {
                            selected = rating
                            comment.visibility = View.VISIBLE
                            send.visibility = View.VISIBLE
                        }
                    }
                })
            }
            column.addView(row)
            column.addView(comment, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            column.addView(send)
            column.addView(textButton(getString(R.string.helpium_rate_skip)) { store.skipRating() })
            return column
        }
    }

    private sealed class Item {
        data class Message(val message: ChatMessage) : Item()
        object Handoff : Item()
        object Typing : Item()
    }

    private inner class MessagesAdapter : BaseAdapter() {
        var items: List<Item> = emptyList()

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun isEnabled(position: Int) = false

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = when (val item = items[position]) {
            is Item.Message -> messageView(item.message, position == items.indexOfLast { it is Item.Message })
            Item.Handoff -> LinearLayout(this@HelpiumActivity).apply {
                setPadding(dp(36), dp(4), 0, dp(4))
                addView(outlinedButton(getString(R.string.helpium_talk_to_human)) { store.requestHuman() })
            }
            Item.Typing -> typingView()
        }
    }

    private fun messageView(message: ChatMessage, isLatest: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(5), 0, dp(5))
        }

        if (message.role == Role.SYSTEM) {
            row.gravity = Gravity.CENTER_HORIZONTAL
            row.addView(secondaryText(message.content).apply { gravity = Gravity.CENTER; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f) })
            return row
        }

        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val maxWidth = (resources.displayMetrics.widthPixels * 0.75).toInt()

        if (message.role == Role.CUSTOMER) {
            row.gravity = Gravity.END
            column.gravity = Gravity.END
            images(message, column, maxWidth)
            if (message.content.isNotEmpty()) column.addView(bubble(message.content, palette.customerBubble, palette.customerBubbleText, maxWidth, palette.customerBubbleText))
            column.alpha = if (message.isPending) 0.6f else 1f
            row.addView(column)
            return row
        }

        val isBot = message.role == Role.BOT
        val name = if (isBot) store.settings?.botName.orEmpty() else message.senderName.orEmpty()
        row.gravity = Gravity.BOTTOM
        row.addView(avatar(if (isBot) store.settings?.botAvatarUrl else message.senderAvatarUrl, name))
        column.setPadding(dp(8), 0, 0, 0)
        column.addView(secondaryText(name).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f) })
        images(message, column, maxWidth)
        if (message.content.isNotEmpty()) column.addView(bubble(message.content, palette.agentBubble, palette.agentBubbleText, maxWidth, palette.link))

        message.attachments.filterNot { it.isImage }.forEach { file ->
            column.addView(textButton("📄 " + (file.name ?: Uri.parse(file.url).lastPathSegment.orEmpty())) { openUrl(file.url) })
        }

        if (message.sources.isNotEmpty()) {
            val links = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
            message.sources.forEach { source ->
                links.addView(textButton(source.title) { source.url?.let(::openUrl) }.apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f) })
            }
            column.addView(textButton("▸ " + getString(R.string.helpium_sources)) {
                links.visibility = if (links.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }.apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f) })
            column.addView(links)
        }

        val interactionsEnabled = isLatest && !store.isBotTyping && !store.isClosed
        when (val interactive = message.interactive) {
            Interactive.Retry -> column.addView(outlinedButton(getString(R.string.helpium_retry)) { store.retry(message.id) })
            Interactive.Confirm -> if (interactionsEnabled) {
                column.addView(LinearLayout(this).apply {
                    addView(primaryButton(getString(R.string.helpium_accept)) { store.send(getString(R.string.helpium_accept)) })
                    addView(outlinedButton(getString(R.string.helpium_cancel)) { store.send(getString(R.string.helpium_cancel)) },
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) })
                })
            }
            is Interactive.Suggestions -> if (interactionsEnabled) {
                interactive.options.forEach { option -> column.addView(outlinedButton(option) { store.send(option) }) }
            }
            null -> Unit
        }

        if (isBot && message.interactive != Interactive.Retry && message.content.isNotEmpty()) {
            column.addView(LinearLayout(this).apply {
                val up = textButton(if (message.feedback == "up") "👍" else "👍🏻") { store.feedback(message.id, true) }
                val down = textButton(if (message.feedback == "down") "👎" else "👎🏻") { store.feedback(message.id, false) }
                listOf(up, down).forEach {
                    it.isEnabled = message.feedback == null
                    it.alpha = if (message.feedback == null || (it == up) == (message.feedback == "up")) 1f else 0.35f
                    addView(it)
                }
            })
        }

        row.addView(column)
        return row
    }

    private fun images(message: ChatMessage, column: LinearLayout, maxWidth: Int) {
        val local = message.localImage
        if (local != null) {
            ImageLoader.decode(local, 1024)?.let { column.addView(imageView(maxWidth).apply { setImageBitmap(it) }) }
            return
        }
        message.attachments.filter { it.isImage }.forEach { attachment ->
            column.addView(imageView(maxWidth).apply {
                ImageLoader.load(attachment.url, this)
                setOnClickListener { openUrl(attachment.url) }
            })
        }
    }

    private fun imageView(maxWidth: Int) = ImageView(this).apply {
        adjustViewBounds = true
        maxHeight = dp(260)
        this.maxWidth = minOf(maxWidth, dp(240))
        scaleType = ImageView.ScaleType.FIT_START
        clipToOutline = true
        background = rounded(palette.surface, dp(14).toFloat())
        setPadding(0, dp(2), 0, dp(2))
    }

    private fun typingView(): View {
        val isBot = store.isBotTyping
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM; setPadding(0, dp(5), 0, dp(5)) }
        row.addView(avatar(if (isBot) store.settings?.botAvatarUrl else store.agent?.avatarUrl, if (isBot) store.settings?.botName.orEmpty() else store.agent?.name.orEmpty()))
        val text = if (isBot && store.botTypingStatus != null) store.botTypingStatus!! else "• • •"
        row.addView(bubble(text, palette.agentBubble, palette.agentBubbleText, dp(260), palette.link).apply {
            alpha = 0.85f
            contentDescription = getString(R.string.helpium_typing)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) })
        return row
    }

    // endregion

    // region Pre-chat and image picking

    private fun prechat(pendingText: String) {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        fun field(hint: Int, type: Int) = EditText(this).apply { this.hint = getString(hint); inputType = type }.also(form::addView)
        form.addView(secondaryText(getString(R.string.helpium_prechat_subtitle)))
        val first = field(R.string.helpium_first_name, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        val last = field(R.string.helpium_last_name, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        val email = field(R.string.helpium_email, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val optIn = CheckBox(this).apply { text = getString(R.string.helpium_marketing_opt_in) }
        if (store.settings?.collectMarketingOptIn == true) form.addView(optIn)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.helpium_prechat_title)
            .setView(form)
            .setPositiveButton(R.string.helpium_continue, null)
            .setNegativeButton(R.string.helpium_cancel, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val firstName = first.text.toString().trim()
            val address = email.text.toString().trim()
            if (firstName.isEmpty()) { first.error = getString(R.string.helpium_first_name); return@setOnClickListener }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()) { email.error = getString(R.string.helpium_email); return@setOnClickListener }
            store.submitPrechat(firstName, last.text.toString().trim(), address, optIn.isChecked)
            store.send(pendingText)
            dialog.dismiss()
        }
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }
        @Suppress("DEPRECATION")
        startActivityForResult(Intent.createChooser(intent, getString(R.string.helpium_attach_image)), PICK_IMAGE)
    }

    @Deprecated("Activity API kept for minSdk 21")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_IMAGE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        Thread {
            val jpeg = runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()?.let(::toJpeg)
            if (jpeg != null) runOnUiThread { store.send("", jpeg, "image/jpeg") }
        }.start()
    }

    /** A JPEG under the 5 MB the API accepts, at most 2048 px on its long side. */
    private fun toJpeg(bytes: ByteArray): ByteArray? {
        val bitmap = ImageLoader.decode(bytes, 2048) ?: return null
        var quality = 85
        while (true) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (out.size() < 4_500_000 || quality <= 25) return out.toByteArray()
            quality -= 15
        }
    }

    // endregion

    // region View helpers

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun rounded(fill: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius
        stroke?.let { setStroke(dp(1), it) }
    }

    private fun bubble(text: String, fill: Int, textColor: Int, maxWidth: Int, linkColor: Int) = TextView(this).apply {
        this.text = Markdown.render(text)
        setTextColor(textColor)
        setLinkTextColor(linkColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTextIsSelectable(true)
        Linkify.addLinks(this, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES)
        movementMethod = LinkMovementMethod.getInstance()
        this.maxWidth = maxWidth
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = rounded(fill, dp(18).toFloat())
    }

    private fun avatar(url: String?, name: String): View {
        val size = dp(28)
        val frame = FrameLayout(this).apply { background = rounded(palette.surface, size / 2f); clipToOutline = true }
        frame.addView(TextView(this).apply {
            text = name.take(1).uppercase()
            gravity = Gravity.CENTER
            setTextColor(palette.textSecondary)
            typeface = Typeface.DEFAULT_BOLD
        }, FrameLayout.LayoutParams(size, size))
        if (url != null) {
            frame.addView(ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; ImageLoader.load(url, this, 128) }, FrameLayout.LayoutParams(size, size))
        }
        frame.layoutParams = LinearLayout.LayoutParams(size, size)
        return frame
    }

    private fun secondaryText(value: String) = TextView(this).apply {
        text = value
        setTextColor(palette.textSecondary)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    }

    private fun primaryButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(onAccent)
        typeface = Typeface.DEFAULT_BOLD
        background = RippleDrawable(ColorStateList.valueOf(0x33000000), rounded(accent, dp(14).toFloat()), null)
        stateListAnimator = null
        setOnClickListener { onClick() }
    }

    private fun outlinedButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(accent)
        background = RippleDrawable(ColorStateList.valueOf(0x22000000), rounded(Color.TRANSPARENT, dp(12).toFloat(), accent), null)
        stateListAnimator = null
        minHeight = dp(40)
        setPadding(dp(14), 0, dp(14), 0)
        setOnClickListener { onClick() }
    }

    private fun outlinedRow(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        setTextColor(palette.text)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = RippleDrawable(ColorStateList.valueOf(0x22000000), rounded(palette.surface, dp(12).toFloat(), palette.border), null)
        setOnClickListener { onClick() }
    }

    private fun textButton(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        setTextColor(accent)
        setPadding(dp(4), dp(6), dp(8), dp(6))
        setOnClickListener { onClick() }
    }

    private fun iconButton(icon: Int, description: Int, onClick: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(palette.headerText)
        background = null
        contentDescription = getString(description)
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        setOnClickListener { onClick() }
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(palette.border)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1).coerceAtLeast(1))
    }

    private fun centered(view: View) = FrameLayout(this).apply {
        addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        setPadding(dp(24), dp(24), dp(24), dp(24))
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    // endregion

    companion object {
        private const val PICK_IMAGE = 4210
    }
}
