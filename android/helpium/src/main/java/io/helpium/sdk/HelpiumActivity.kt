package io.helpium.sdk

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** The chat: home, thread, pre-chat form and rating. Built in code so the SDK needs no androidx. */
class HelpiumActivity : Activity() {
    private lateinit var store: ChatStore
    private lateinit var root: FrameLayout
    private var palette: Palette? = null
    private var shown: ChatStore.Screen? = null
    private var thread: ThreadUi? = null
    private val listener: () -> Unit = { render() }

    private val p get() = palette!!

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
        root = FrameLayout(this)
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
        super.onBackPressed()
    }

    // region Rendering

    private fun resolvePalette(): Palette {
        val dark = when (store.appearance.colorScheme) {
            HelpiumColorScheme.LIGHT -> false
            HelpiumColorScheme.DARK -> true
            HelpiumColorScheme.SYSTEM -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        return Palette.resolve(store.appearance, store.settings?.primaryColor, dark)
    }

    private fun render() {
        val fresh = resolvePalette()
        val screen = store.screen
        val rebuild = fresh != palette || screen != shown || screen != ChatStore.Screen.THREAD
        palette = fresh

        store.notice?.let {
            store.notice = null
            AlertDialog.Builder(this).setMessage(it).setPositiveButton(android.R.string.ok, null).show()
        }

        if (!rebuild) {
            thread?.render()
            return
        }

        shown = screen
        thread = null
        root.removeAllViews()
        root.setBackgroundColor(p.background)
        window.decorView.setBackgroundColor(p.background)

        when (screen) {
            ChatStore.Screen.THREAD -> {
                statusBar(p.headerBackground)
                thread = ThreadUi().also { root.addView(it.root); it.render() }
            }
            ChatStore.Screen.LOADING -> {
                statusBar(p.headerBackground)
                root.addView(simpleScreen(ProgressBar(this).apply { indeterminateTintList = ColorStateList.valueOf(p.textSecondary) }))
            }
            ChatStore.Screen.FAILED -> {
                statusBar(p.headerBackground)
                root.addView(simpleScreen(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    addView(text(getString(R.string.helpium_loading_failed), 15f, p.textSecondary).apply { gravity = Gravity.CENTER })
                    addView(pill(getString(R.string.helpium_retry), filled = true) { store.reload() }, wrap().apply { topMargin = dp(16) })
                }))
            }
            ChatStore.Screen.UNAVAILABLE -> {
                statusBar(p.headerBackground)
                root.addView(simpleScreen(text(getString(R.string.helpium_offline), 15f, p.textSecondary).apply { gravity = Gravity.CENTER }))
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun statusBar(color: Int) {
        window.statusBarColor = color
        if (Build.VERSION.SDK_INT >= 23) {
            val flags = window.decorView.systemUiVisibility
            val light = Palette.contrasting(color) == Color.BLACK
            window.decorView.systemUiVisibility = if (light) flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
    }

    private fun simpleScreen(content: View): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(LinearLayout(this@HelpiumActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(10), dp(8))
            setBackgroundColor(p.headerBackground)
            addView(text(getString(R.string.helpium_title), 17f, p.headerText, bold = true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(closeButton(p.headerText))
        })
        addView(FrameLayout(this@HelpiumActivity).apply {
            setPadding(dp(32), dp(32), dp(32), dp(32))
            addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    // endregion

    private fun senderName(message: ChatMessage): String = when (message.role) {
        Role.CUSTOMER -> getString(R.string.helpium_you)
        Role.AGENT -> message.senderName ?: getString(R.string.helpium_title)
        else -> store.settings?.botName ?: getString(R.string.helpium_title)
    }

    // endregion

    // region Thread

    private inner class ThreadUi {
        val root = LinearLayout(this@HelpiumActivity).apply { orientation = LinearLayout.VERTICAL }
        private val headerName = text("", 15f, p.headerText, bold = true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        private val headerStatus = text("", 12f, withAlpha(p.headerText, 0.65f)).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        private val headerAvatar = FrameLayout(this@HelpiumActivity)
        private val headerLines = LinearLayout(this@HelpiumActivity)

        /** The small line goes above the main one while greeting, below it in a conversation. */
        private fun swapHeaderLines(statusFirst: Boolean) {
            val first = headerLines.getChildAt(0)
            if ((statusFirst && first === headerStatus) || (!statusFirst && first === headerName)) return
            headerLines.removeAllViews()
            if (statusFirst) { headerLines.addView(headerStatus); headerLines.addView(headerName) } else { headerLines.addView(headerName); headerLines.addView(headerStatus) }
            headerStatus.textSize = 12f
        }
        private val moreButton = iconButton(R.drawable.helpium_ic_more, p.headerText, R.string.helpium_more) { confirmClose() }
        private val adapter = MessagesAdapter()
        private val list = ListView(this@HelpiumActivity).apply {
            divider = null
            dividerHeight = 0
            selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            clipToPadding = false
            setPadding(dp(14), 0, dp(14), dp(14))
            transcriptMode = ListView.TRANSCRIPT_MODE_NORMAL
            isStackFromBottom = true
            isVerticalScrollBarEnabled = false
            this.adapter = this@ThreadUi.adapter
        }
        private val footer = FrameLayout(this@HelpiumActivity)
        private var input: EditText? = null
        private var sendButton: FrameLayout? = null
        private var sendIcon: ImageView? = null
        private var footerState = ""
        private var lastCount = 0
        private var lastAvatar: String? = "-"

        init {
            root.addView(LinearLayout(this@HelpiumActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), dp(8), dp(10), dp(8))
                setBackgroundColor(p.headerBackground)
                addView(headerAvatar, LinearLayout.LayoutParams(dp(36), dp(36)).apply { leftMargin = dp(8) })
                addView(headerLines.apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(10), 0, dp(4), 0)
                    addView(headerName)
                    addView(headerStatus)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(moreButton)
                addView(closeButton(p.headerText))
            })
            root.addView(divider(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
            root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            root.addView(divider(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
            root.addView(footer)
        }

        fun render() {
            // Like the web widget: an empty chat greets; a conversation says who is answering and how fast.
            val name = store.agent?.name ?: store.settings?.botName ?: getString(R.string.helpium_title)
            if (!store.hasConversation) {
                headerStatus.text = store.settings?.botName.orEmpty()
                headerStatus.visibility = if (headerStatus.text.isEmpty()) View.GONE else View.VISIBLE
                headerName.text = getString(R.string.helpium_greeting)
                headerName.maxLines = 2
                swapHeaderLines(statusFirst = true)
            } else {
                headerName.text = name
                headerName.maxLines = 1
                headerStatus.visibility = View.VISIBLE
                headerStatus.text = getString(
                    if (store.agent == null && store.handoffRequested && !store.isClosed) R.string.helpium_waiting_for_agent else R.string.helpium_reply_time
                )
                swapHeaderLines(statusFirst = false)
            }
            val avatarUrl = store.agent?.avatarUrl ?: store.settings?.botAvatarUrl
            if (avatarUrl != lastAvatar || headerAvatar.childCount == 0) {
                lastAvatar = avatarUrl
                headerAvatar.removeAllViews()
                headerAvatar.addView(avatar(avatarUrl, name, 36))
                if (store.settings?.isOnline != false) {
                    headerAvatar.addView(View(this@HelpiumActivity).apply {
                        background = shape(Color.parseColor("#22c55e"), dp(5).toFloat(), stroke = p.headerBackground)
                    }, FrameLayout.LayoutParams(dp(10), dp(10), Gravity.BOTTOM or Gravity.END))
                }
            }
            moreButton.visibility = if (store.hasConversation && !store.isClosed) View.VISIBLE else View.GONE

            adapter.items = buildItems()
            // Messages sit at the bottom like any chat; the welcome sits at the top like the web widget.
            list.isStackFromBottom = store.messages.isNotEmpty()
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
            refreshSend()
        }

        private fun buildItems(): List<Item> {
            val messages = store.messages
            val items = messages.mapIndexed { index, message ->
                Item.Message(
                    message,
                    isFirst = !sameSender(messages.getOrNull(index - 1), message),
                    isLast = !sameSender(message, messages.getOrNull(index + 1)),
                    isLatest = index == messages.lastIndex,
                )
            }.toMutableList<Item>()
            if (messages.isEmpty()) items.add(0, Item.Welcome)
            if (store.showHandoffButton) items += Item.Handoff
            if (store.isBotTyping || store.isAgentTyping) items += Item.Typing
            return items
        }

        private fun composer(): View {
            val row = LinearLayout(this@HelpiumActivity).apply {
                gravity = Gravity.BOTTOM
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundColor(p.background)
            }
            val field = EditText(this@HelpiumActivity).apply {
                hint = getString(R.string.helpium_placeholder)
                background = null
                setPadding(dp(14), dp(11), dp(4), dp(11))
                setTextColor(p.inputText)
                setHintTextColor(p.textSecondary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                maxLines = 5
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) {
                        if (!s.isNullOrEmpty()) store.customerTyping()
                        refreshSend()
                    }
                })
            }
            input = field
            val box = LinearLayout(this@HelpiumActivity).apply {
                gravity = Gravity.BOTTOM
                background = shape(p.inputBackground, dp(22).toFloat(), stroke = p.border)
                addView(field, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(iconButton(R.drawable.helpium_ic_attach, p.textSecondary, R.string.helpium_attach_image, size = 42) {
                    if (!store.needsPrechat) pickImage()
                })
            }
            val icon = icon(R.drawable.helpium_ic_arrow_up, p.textSecondary, 20)
            val send = FrameLayout(this@HelpiumActivity).apply {
                contentDescription = getString(R.string.helpium_send)
                isClickable = true
                setOnClickListener { send() }
                addView(icon, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
            }
            sendButton = send
            sendIcon = icon
            row.addView(box, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(send, LinearLayout.LayoutParams(dp(44), dp(44)).apply { leftMargin = dp(8) })
            return row
        }

        private fun refreshSend() {
            val can = !input?.text.isNullOrBlank() && !store.isBotTyping
            sendButton?.background = shape(if (can) p.primary else p.surface, dp(22).toFloat())
            sendButton?.isEnabled = can
            sendIcon?.imageTintList = ColorStateList.valueOf(if (can) p.onPrimary else p.textSecondary)
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
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(p.background)
            addView(text(getString(when (store.closedReason) {
                "customer" -> R.string.helpium_closed_customer
                "inactivity" -> R.string.helpium_closed_inactivity
                "agent", "rule", "unrelated", "automated" -> R.string.helpium_closed_team
                else -> R.string.helpium_closed
            }), 13f, p.textSecondary).apply { gravity = Gravity.CENTER })
            addView(pill(getString(R.string.helpium_start_new), filled = true) { store.startNewConversation() }, wrap().apply { topMargin = dp(12) })
        }

        private fun rating(): View {
            val column = LinearLayout(this@HelpiumActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(16), dp(16), dp(16), dp(12))
                setBackgroundColor(p.background)
            }
            column.addView(text(getString(R.string.helpium_rate_title), 16f, p.text, bold = true))
            val faces = listOf("😞", "😐", "🙂", "😀", "🤩")
            val labels = listOf(R.string.helpium_rating_1, R.string.helpium_rating_2, R.string.helpium_rating_3, R.string.helpium_rating_4, R.string.helpium_rating_5)
            val comment = EditText(this@HelpiumActivity).apply {
                hint = getString(R.string.helpium_rate_comment)
                setTextColor(p.inputText)
                setHintTextColor(p.textSecondary)
                background = shape(p.inputBackground, dp(12).toFloat(), stroke = p.border)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                visibility = View.GONE
            }
            var selected = 0
            val send = pill(getString(R.string.helpium_rate_send), filled = true) { store.rate(selected, comment.text.toString()) }.apply { visibility = View.GONE }
            val faceViews = mutableListOf<View>()
            val row = LinearLayout(this@HelpiumActivity).apply { gravity = Gravity.CENTER }
            faces.forEachIndexed { index, face ->
                val rating = index + 1
                val option = LinearLayout(this@HelpiumActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(dp(8), dp(10), dp(8), dp(6))
                    addView(TextView(this@HelpiumActivity).apply { text = face; setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f) })
                    addView(text(getString(labels[index]), 11f, p.textSecondary))
                    setOnClickListener {
                        if (rating >= 3) {
                            store.rate(rating, null)
                        } else {
                            selected = rating
                            comment.visibility = View.VISIBLE
                            send.visibility = View.VISIBLE
                            faceViews.forEachIndexed { i, v -> v.alpha = if (i == index) 1f else 0.35f }
                        }
                    }
                }
                faceViews += option
                row.addView(option)
            }
            column.addView(row)
            column.addView(comment, match().apply { topMargin = dp(8) })
            column.addView(send, wrap().apply { topMargin = dp(10) })
            column.addView(text(getString(R.string.helpium_rate_skip), 13f, p.textSecondary).apply {
                setPadding(dp(12), dp(10), dp(12), dp(4))
                setOnClickListener { store.skipRating() }
            })
            return column
        }
    }

    private fun sameSender(a: ChatMessage?, b: ChatMessage?): Boolean {
        if (a == null || b == null || a.role != b.role || a.role == Role.SYSTEM) return false
        return a.role != Role.AGENT || a.senderName == b.senderName
    }

    private sealed class Item {
        data class Message(val message: ChatMessage, val isFirst: Boolean, val isLast: Boolean, val isLatest: Boolean) : Item()
        object Welcome : Item()
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
            is Item.Message -> messageView(item).apply { setPadding(paddingLeft, if (item.isFirst) dp(14) else dp(3), paddingRight, 0) }
            Item.Handoff -> FrameLayout(this@HelpiumActivity).apply {
                setPadding(0, dp(14), 0, 0)
                addView(pill(getString(R.string.helpium_talk_to_human), filled = false, iconRes = R.drawable.helpium_ic_person) { store.requestHuman() },
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL))
            }
            Item.Typing -> typingView().apply { setPadding(0, dp(14), 0, 0) }
            Item.Welcome -> welcomeView()
        }
    }

    private fun messageView(item: Item.Message): LinearLayout {
        val message = item.message
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val maxWidth = (resources.displayMetrics.widthPixels * 0.74).toInt()

        when (message.role) {
            Role.SYSTEM -> {
                column.gravity = Gravity.CENTER_HORIZONTAL
                column.addView(text(message.content, 12f, p.textSecondary).apply {
                    gravity = Gravity.CENTER
                    background = shape(p.surface, dp(14).toFloat())
                    setPadding(dp(12), dp(6), dp(12), dp(6))
                }, wrap().apply { leftMargin = dp(24); rightMargin = dp(24) })
            }
            Role.CUSTOMER -> {
                column.gravity = Gravity.END
                images(message, column, maxWidth)
                if (message.content.isNotEmpty()) {
                    column.addView(bubble(message.content, p.customerBubble, p.customerBubbleText, p.customerBubbleText, maxWidth, customer = true, last = item.isLast).apply {
                        alpha = if (message.isPending) 0.6f else 1f
                    }, wrap().apply { leftMargin = dp(56) })
                }
                if (item.isLast) {
                    val label = if (message.isPending) "…" else (if (message.seenAt != null) "✓✓ " else "") + timeLabel(message.createdAt)
                    column.addView(text(label, 11f, p.textSecondary).apply { setPadding(0, dp(4), dp(2), 0) }, wrap())
                }
            }
            Role.BOT, Role.AGENT -> {
                val isBot = message.role == Role.BOT
                val name = senderName(message)
                val row = LinearLayout(this).apply { gravity = Gravity.BOTTOM }
                row.addView(
                    if (item.isLast) avatar(if (isBot) store.settings?.botAvatarUrl else message.senderAvatarUrl, name, 28) else View(this),
                    LinearLayout.LayoutParams(dp(28), dp(28)),
                )
                val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, dp(40), 0) }
                images(message, content, maxWidth)
                if (message.content.isNotEmpty()) content.addView(bubble(message.content, p.agentBubble, p.agentBubbleText, p.link, maxWidth, customer = false, last = item.isLast), wrap())
                message.attachments.filterNot { it.isImage }.forEach { file -> content.addView(fileChip(file), wrap().apply { topMargin = dp(6) }) }
                if (message.sources.isNotEmpty()) content.addView(sources(message))
                interactive(message, item.isLatest)?.let { content.addView(it, wrap().apply { topMargin = dp(8) }) }
                row.addView(content)
                column.addView(row)

                if (item.isLast) {
                    column.addView(LinearLayout(this).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(36), dp(4), 0, 0)
                        val label = if (isBot) "$name · ${getString(R.string.helpium_ai)} · ${timeLabel(message.createdAt)}" else "$name · ${timeLabel(message.createdAt)}"
                        addView(text(label, 11f, p.textSecondary))
                        if (isBot && message.interactive != Interactive.Retry && message.content.isNotEmpty()) {
                            listOf(true, false).forEach { up ->
                                val chosen = message.feedback == (if (up) "up" else "down")
                                addView(iconButton(if (up) R.drawable.helpium_ic_thumb_up else R.drawable.helpium_ic_thumb_down,
                                    if (chosen) p.primary else p.textSecondary, R.string.helpium_send, size = 28, iconSize = 14) {
                                    if (message.feedback == null) store.feedback(message.id, up)
                                }.apply { alpha = if (message.feedback == null || chosen) 1f else 0.35f })
                            }
                        }
                    })
                }
            }
        }
        return column
    }

    /** What an empty chat shows, like the web widget: a title, what to do, and one-tap instant answers. */
    private fun welcomeView(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(16), dp(40), dp(16), dp(12))
        addView(text(getString(R.string.helpium_welcome_title), 22f, p.text, bold = true).apply { gravity = Gravity.CENTER }, wrap())
        val subtitle = if (store.settings?.aiAvailable == false) R.string.helpium_welcome_subtitle_team else R.string.helpium_welcome_subtitle
        addView(text(getString(subtitle), 14f, p.textSecondary).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), 0)
            setLineSpacing(0f, 1.15f)
        }, wrap())
        val answers = store.settings?.instantAnswers.orEmpty()
        if (answers.isNotEmpty()) {
            addView(text(getString(R.string.helpium_instant_answers).uppercase(), 11f, p.textSecondary, bold = true).apply {
                setPadding(0, dp(28), 0, dp(10))
            }, wrap())
            answers.forEach { answer ->
                addView(pill(answer.label, filled = false) { store.sendInstantAnswer(answer) }, wrap().apply { bottomMargin = dp(8) })
            }
        }
    }

    private fun interactive(message: ChatMessage, isLatest: Boolean): View? {
        val enabled = isLatest && !store.isBotTyping && !store.isClosed
        return when (val interactive = message.interactive) {
            Interactive.Retry -> pill(getString(R.string.helpium_retry), filled = false) { store.retry(message.id) }
            Interactive.Confirm -> if (!enabled) null else LinearLayout(this).apply {
                addView(pill(getString(R.string.helpium_accept), filled = true) { store.send(getString(R.string.helpium_accept)) })
                addView(pill(getString(R.string.helpium_cancel), filled = false) { store.send(getString(R.string.helpium_cancel)) }, wrap().apply { leftMargin = dp(8) })
            }
            is Interactive.Suggestions -> if (!enabled) null else LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                interactive.options.forEach { option -> addView(pill(option, filled = false) { store.send(option) }, wrap().apply { bottomMargin = dp(6) }) }
            }
            null -> null
        }
    }

    private fun sources(message: ChatMessage): View {
        val links = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        message.sources.forEach { source ->
            links.addView(text(source.title, 13f, p.link).apply {
                setPadding(0, dp(4), 0, dp(4))
                setOnClickListener { source.url?.let(::openUrl) }
            })
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(6), 0, 0)
            addView(text("▸ " + getString(R.string.helpium_sources), 12f, p.link).apply {
                setOnClickListener { links.visibility = if (links.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
            })
            addView(links)
        }
    }

    private fun fileChip(file: Attachment): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(10), dp(14), dp(10))
        background = shape(p.surface, dp(12).toFloat(), stroke = p.border)
        setOnClickListener { openUrl(file.url) }
        addView(icon(R.drawable.helpium_ic_doc, p.primary, 18))
        addView(text(file.name ?: Uri.parse(file.url).lastPathSegment.orEmpty(), 14f, p.text).apply {
            setPadding(dp(8), 0, 0, 0)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
        })
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
            }, wrap().apply { bottomMargin = dp(4) })
        }
    }

    private fun imageView(maxWidth: Int) = ImageView(this).apply {
        adjustViewBounds = true
        maxHeight = dp(280)
        this.maxWidth = minOf(maxWidth, dp(240))
        minimumWidth = dp(120)
        minimumHeight = dp(90)
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = shape(p.surface, dp(16).toFloat())
        clipToOutline = true
    }

    private fun typingView(): View {
        val isBot = store.isBotTyping
        val row = LinearLayout(this).apply { gravity = Gravity.BOTTOM; clipChildren = false }
        row.addView(avatar(if (isBot) store.settings?.botAvatarUrl else store.agent?.avatarUrl,
            if (isBot) store.settings?.botName.orEmpty() else store.agent?.name.orEmpty(), 28), LinearLayout.LayoutParams(dp(28), dp(28)))
        val bubble = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = bubbleShape(p.agentBubble, customer = false, last = true)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            clipChildren = false
            clipToPadding = false
            contentDescription = getString(R.string.helpium_typing)
        }
        val status = store.botTypingStatus
        if (isBot && status != null) {
            bubble.addView(ProgressBar(this).apply { indeterminateTintList = ColorStateList.valueOf(withAlpha(p.agentBubbleText, 0.6f)) }, LinearLayout.LayoutParams(dp(16), dp(16)))
            bubble.addView(text(status, 13f, withAlpha(p.agentBubbleText, 0.75f)).apply { setPadding(dp(8), 0, 0, 0) })
        } else {
            repeat(3) { index ->
                val dot = View(this).apply { background = shape(withAlpha(p.agentBubbleText, 0.45f), dp(4).toFloat()) }
                bubble.addView(dot, LinearLayout.LayoutParams(dp(7), dp(7)).apply { if (index > 0) leftMargin = dp(5) })
                ObjectAnimator.ofFloat(dot, View.TRANSLATION_Y, dp(3).toFloat(), -dp(3).toFloat()).apply {
                    duration = 450
                    startDelay = index * 150L
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    start()
                }
            }
        }
        row.addView(bubble, wrap().apply { leftMargin = dp(8) })
        return row
    }

    // endregion

    // region Dialogs and image picking

    private fun confirmClose() {
        AlertDialog.Builder(this)
            .setTitle(R.string.helpium_close_confirm_title)
            .setMessage(R.string.helpium_close_confirm_message)
            .setPositiveButton(R.string.helpium_end) { _, _ -> store.close() }
            .setNegativeButton(R.string.helpium_cancel, null)
            .show()
    }

    private fun prechat(pendingText: String) {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        fun field(hint: Int, type: Int) = EditText(this).apply { this.hint = getString(hint); inputType = type }.also(form::addView)
        form.addView(TextView(this).apply { text = getString(R.string.helpium_prechat_subtitle) })
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

    private fun match() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun wrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun withAlpha(color: Int, alpha: Float) = (color and 0x00FFFFFF) or ((alpha * 255).toInt() shl 24)

    private fun shape(fill: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius
        stroke?.let { setStroke(maxOf(1, dp(1) / 2 + 1), it) }
    }

    /** Fully rounded, with a tighter corner pointing at the sender on the last message of a group. */
    private fun bubbleShape(fill: Int, customer: Boolean, last: Boolean) = GradientDrawable().apply {
        setColor(fill)
        val big = dp(18).toFloat()
        val small = if (last) dp(5).toFloat() else big
        val tl = if (customer) big else small
        val tr = if (customer) small else big
        val br = if (customer) dp(5).toFloat() else big
        val bl = if (customer) big else dp(5).toFloat()
        cornerRadii = floatArrayOf(tl, tl, tr, tr, br, br, bl, bl)
    }

    private fun bubble(content: String, fill: Int, textColor: Int, linkColor: Int, maxWidth: Int, customer: Boolean, last: Boolean) = TextView(this).apply {
        text = linkified(content)
        setTextColor(textColor)
        setLinkTextColor(linkColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
        setLineSpacing(0f, 1.12f)
        setTextIsSelectable(true)
        movementMethod = LinkMovementMethod.getInstance()
        this.maxWidth = maxWidth
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = bubbleShape(fill, customer, last)
    }

    /** Markdown links plus bare URLs and emails. Linkify drops existing links, so the markdown ones are put back. */
    private fun linkified(content: String): CharSequence {
        val spannable = android.text.SpannableString(Markdown.render(content))
        val markdownLinks = spannable.getSpans(0, spannable.length, android.text.style.URLSpan::class.java)
            .map { Triple(spannable.getSpanStart(it), spannable.getSpanEnd(it), it.url) }
        Linkify.addLinks(spannable, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES)
        markdownLinks.forEach { (start, end, url) ->
            spannable.setSpan(android.text.style.URLSpan(url), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return spannable
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

    private fun icon(res: Int, tint: Int, size: Int) = ImageView(this).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(tint)
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }

    private fun iconButton(res: Int, tint: Int, description: Int, size: Int = 40, iconSize: Int = 22, onClick: () -> Unit) = ImageButton(this).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(tint)
        background = RippleDrawable(ColorStateList.valueOf(0x22000000), null, shape(Color.WHITE, dp(size).toFloat()))
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        val inset = dp((size - iconSize) / 2)
        setPadding(inset, inset, inset, inset)
        contentDescription = getString(description)
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        setOnClickListener { onClick() }
    }

    private fun closeButton(color: Int) = iconButton(R.drawable.helpium_ic_close, color, R.string.helpium_close, size = 34, iconSize = 18) { finish() }.apply {
        background = RippleDrawable(ColorStateList.valueOf(0x22000000), shape(withAlpha(color, 0.12f), dp(17).toFloat()), null)
    }

    private fun pill(label: String, filled: Boolean, iconRes: Int? = null, onClick: () -> Unit) = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(9), dp(16), dp(9))
        background = RippleDrawable(ColorStateList.valueOf(0x22000000),
            shape(if (filled) p.primary else Color.TRANSPARENT, dp(20).toFloat(), stroke = if (filled) null else p.primary), null)
        isClickable = true
        setOnClickListener { onClick() }
        val color = if (filled) p.onPrimary else p.primary
        iconRes?.let { addView(icon(it, color, 16), LinearLayout.LayoutParams(dp(16), dp(16)).apply { rightMargin = dp(6) }) }
        addView(text(label, 14f, color, bold = true))
    }

    private fun badge(count: Int) = text(count.toString(), 11f, p.unreadBadgeText, bold = true).apply {
        gravity = Gravity.CENTER
        minWidth = dp(20)
        setPadding(dp(6), dp(2), dp(6), dp(2))
        background = shape(p.unreadBadge, dp(10).toFloat())
    }

    private fun avatar(url: String?, name: String, size: Int, ring: Int? = null): View {
        val px = dp(size)
        val frame = FrameLayout(this).apply {
            background = shape(p.surface, px / 2f, stroke = ring)
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(px, px)
        }
        frame.addView(text(name.take(1).uppercase(), size * 0.42f, p.textSecondary, bold = true).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(px, px))
        if (url != null) {
            frame.addView(ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; ImageLoader.load(url, this, 128) }, FrameLayout.LayoutParams(px, px))
        }
        return frame
    }

    private fun divider() = View(this).apply { setBackgroundColor(p.border) }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    /** "Just now", "6:50 PM" today, "Oct 14, 6:50 PM" before. */
    private fun timeLabel(iso: String): String {
        val date = parseIso(iso) ?: return ""
        if (System.currentTimeMillis() - date.time < 60_000) return getString(R.string.helpium_just_now)
        val locale = Locale(Strings.locale(this))
        val today = Calendar.getInstance().apply { time = Date() }
        val then = Calendar.getInstance().apply { time = date }
        val sameDay = today.get(Calendar.YEAR) == then.get(Calendar.YEAR) && today.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
        val time = android.text.format.DateFormat.getTimeFormat(this).format(date)
        return if (sameDay) time else SimpleDateFormat("MMM d", locale).format(date) + ", " + time
    }

    // endregion

    companion object {
        private const val PICK_IMAGE = 4210

        internal fun parseIso(iso: String): Date? {
            val clean = iso.replace(Regex("\\.\\d+"), "").replace("Z", "+0000").replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2")
            return runCatching {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(clean)
            }.getOrNull()
        }
    }
}
