import Foundation

/// Every text the chat shows, in English and Spanish. The host can override
/// any of them with `Helpium.setString(_:for:)`.
enum L {
    static var locale: String = "en"
    static var overrides: [String: String] = [:]

    /// A text, looked up in order: `Helpium.setString`, the host app's
    /// `Localizable.strings` for the chat language (key `helpium_<key>`, the
    /// same names as the Android resources), then the SDK's own en/es texts.
    static func t(_ key: String, _ args: CVarArg...) -> String {
        let template = overrides[key] ?? hostString(key) ?? (locale == "es" ? es[key] : nil) ?? en[key] ?? key
        return args.isEmpty ? template : String(format: template, arguments: args)
    }

    private static func hostString(_ key: String) -> String? {
        let name = "helpium_\(key)"
        let bundle = Bundle.main.path(forResource: locale, ofType: "lproj").flatMap(Bundle.init(path:)) ?? Bundle.main
        let value = bundle.localizedString(forKey: name, value: nil, table: nil)
        return value == name ? nil : value
    }

    static let en: [String: String] = [
        "greeting": "Hi! How can I help you today?",
        "welcome_subtitle_team": "Send us a message and our team will get back to you shortly.",
        "instant_answers": "Instant answers",
        "title": "Support",
        "welcome_title": "How can we help?",
        "welcome_subtitle": "Type your question below and we'll find relevant articles or connect you with support.",
        "reply_time": "We typically reply within a few minutes",
        "just_now": "Just now",
        "more": "More options",
        "attach_image": "Attach an image",
        "send": "Send",
        "placeholder": "Type your message...",
        "talk_to_human": "Talk to a human",
        "waiting_for_agent": "Waiting for agent",
        "chatting_with": "Chatting with %@",
        "offline": "We are away right now. Leave a message and we will get back to you.",
        "close_conversation": "End conversation",
        "close_confirm_title": "End this conversation?",
        "close_confirm_message": "You can always start a new one.",
        "cancel": "Cancel",
        "end": "End",
        "closed_customer": "You ended this conversation",
        "closed_inactivity": "This conversation was closed due to inactivity",
        "closed_team": "Our team closed this conversation",
        "closed": "This conversation is closed",
        "start_new": "Start a new conversation",
        "rate_title": "How was your conversation?",
        "rate_comment": "Tell us what went wrong (optional)",
        "rate_send": "Send",
        "rate_skip": "Skip",
        "rate_thanks": "Thanks for your feedback",
        "rating_1": "Poor", "rating_2": "Okay", "rating_3": "Good", "rating_4": "Great", "rating_5": "Amazing",
        "prechat_title": "Before we start",
        "prechat_subtitle": "So we can follow up if you leave the app.",
        "first_name": "First name",
        "last_name": "Last name",
        "email": "Email",
        "marketing_opt_in": "Send me news and offers",
        "continue": "Continue",
        "retry": "Try again",
        "accept": "Accept",
        "sources": "Related links",
        "photo_library": "Photo library",
        "camera": "Camera",
        "send_failed": "The message could not be sent. Try again.",
        "loading_failed": "The chat could not load. Check your connection.",
        "typing": "Typing...",
        "image": "Image",
        "close": "Close",
    ]

    static let es: [String: String] = [
        "greeting": "¡Hola! ¿En qué puedo ayudarte hoy?",
        "welcome_subtitle_team": "Envíanos un mensaje y nuestro equipo te responderá pronto.",
        "instant_answers": "Respuestas instantáneas",
        "title": "Soporte",
        "welcome_title": "¿Cómo podemos ayudarte?",
        "welcome_subtitle": "Escribe tu pregunta y buscaremos artículos relevantes o te conectaremos con soporte.",
        "reply_time": "Normalmente respondemos en pocos minutos",
        "just_now": "Ahora",
        "more": "Más opciones",
        "attach_image": "Adjuntar una imagen",
        "send": "Enviar",
        "placeholder": "Escribe tu mensaje...",
        "talk_to_human": "Hablar con una persona",
        "waiting_for_agent": "Esperando agente",
        "chatting_with": "Chateando con %@",
        "offline": "Ahora no estamos disponibles. Déjanos un mensaje y te responderemos.",
        "close_conversation": "Terminar conversación",
        "close_confirm_title": "¿Terminar esta conversación?",
        "close_confirm_message": "Siempre puedes empezar una nueva.",
        "cancel": "Cancelar",
        "end": "Terminar",
        "closed_customer": "Terminaste esta conversación",
        "closed_inactivity": "La conversación se cerró por inactividad",
        "closed_team": "Nuestro equipo cerró esta conversación",
        "closed": "Esta conversación está cerrada",
        "start_new": "Empezar una nueva conversación",
        "rate_title": "¿Cómo fue la conversación?",
        "rate_comment": "Cuéntanos qué salió mal (opcional)",
        "rate_send": "Enviar",
        "rate_skip": "Omitir",
        "rate_thanks": "Gracias por tu opinión",
        "rating_1": "Mala", "rating_2": "Regular", "rating_3": "Buena", "rating_4": "Muy buena", "rating_5": "Excelente",
        "prechat_title": "Antes de empezar",
        "prechat_subtitle": "Así podemos responderte aunque salgas de la app.",
        "first_name": "Nombre",
        "last_name": "Apellido",
        "email": "Email",
        "marketing_opt_in": "Quiero recibir novedades y ofertas",
        "continue": "Continuar",
        "retry": "Reintentar",
        "accept": "Aceptar",
        "sources": "Enlaces relacionados",
        "photo_library": "Fotos",
        "camera": "Cámara",
        "send_failed": "No se pudo enviar el mensaje. Inténtalo de nuevo.",
        "loading_failed": "No se pudo cargar el chat. Revisa tu conexión.",
        "typing": "Escribiendo...",
        "image": "Imagen",
        "close": "Cerrar",
    ]
}
