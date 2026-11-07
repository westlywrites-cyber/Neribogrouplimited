package com.westly.neribogroup.data

/**
 * The words used to write proposals: project types, requirement pools, names and countries.
 * Everything here is a simulation. All names, companies, emails and phone numbers are made up.
 */

/** One kind of project a client can ask for. {client} and {country} are filled in when a proposal is made. */
internal data class SoftwareType(
    val code: String,
    val title: String,
    val kind: OfferKind,
    val minNgn: Long,
    val maxNgn: Long,
    val allowCompany: Boolean,
    val allowIndividual: Boolean,
    val summaries: List<String>,
    val pool: List<String>
)

internal val SOFTWARE_TYPES: List<SoftwareType> = listOf(
    SoftwareType(
        code = "W1", title = "Company website", kind = OfferKind.WEBSITE,
        minNgn = 300_000L, maxNgn = 700_000L, allowCompany = true, allowIndividual = false,
        summaries = listOf(
            "{client} needs a professional website to present its services and win new customers online.",
            "{client} wants to replace its old website with a modern, mobile-friendly one."
        ),
        pool = listOf(
            "Home, About, Services and Contact pages", "Contact form that emails the owner",
            "WhatsApp chat button", "Photo gallery", "News/blog section", "Google Maps location",
            "Social media links", "Basic SEO setup"
        )
    ),
    SoftwareType(
        code = "W2", title = "Portfolio website", kind = OfferKind.WEBSITE,
        minNgn = 300_000L, maxNgn = 500_000L, allowCompany = false, allowIndividual = true,
        summaries = listOf(
            "{client} wants a personal portfolio website to show past work and attract clients.",
            "{client} needs a clean one-page portfolio with a contact section and a downloadable CV."
        ),
        pool = listOf(
            "One-page design with smooth sections", "Project gallery with captions",
            "Downloadable CV (PDF)", "Contact form", "Short biography page", "Testimonials section",
            "Links to social profiles", "Fast loading on mobile data"
        )
    ),
    SoftwareType(
        code = "W3", title = "School website", kind = OfferKind.WEBSITE,
        minNgn = 400_000L, maxNgn = 900_000L, allowCompany = true, allowIndividual = false,
        summaries = listOf(
            "{client} is a school that wants a website for admissions, news and parent information.",
            "{client} needs a website where parents can read school news and apply for admission online."
        ),
        pool = listOf(
            "Online admission form", "News and events page", "Staff profiles",
            "Gallery of school activities", "Downloadable fee schedule", "Contact and map page",
            "Notice board", "Mobile-friendly layout"
        )
    ),
    SoftwareType(
        code = "W4", title = "Church / NGO website", kind = OfferKind.WEBSITE,
        minNgn = 400_000L, maxNgn = 900_000L, allowCompany = true, allowIndividual = false,
        summaries = listOf(
            "{client} wants a website for its programmes, sermons or projects, and online donations.",
            "{client} needs a website to share its work, publish events and receive donations."
        ),
        pool = listOf(
            "Donation page with bank details and card option", "Events calendar",
            "Sermon or report downloads", "Volunteer sign-up form", "Photo and video gallery",
            "Newsletter sign-up", "About and leadership page", "Prayer or enquiry form"
        )
    ),
    SoftwareType(
        code = "W5", title = "Restaurant website", kind = OfferKind.WEBSITE,
        minNgn = 400_000L, maxNgn = 900_000L, allowCompany = true, allowIndividual = true,
        summaries = listOf(
            "{client} wants a restaurant website with an online menu and easy ordering by WhatsApp.",
            "{client} needs a website to show its menu, location and take table reservations."
        ),
        pool = listOf(
            "Menu with photos and prices", "Order by WhatsApp button", "Table reservation form",
            "Opening hours and location map", "Photo gallery", "Customer reviews section",
            "Special offers banner", "Contact page"
        )
    ),
    SoftwareType(
        code = "W6", title = "Real-estate listings website", kind = OfferKind.WEBSITE,
        minNgn = 800_000L, maxNgn = 2_000_000L, allowCompany = true, allowIndividual = true,
        summaries = listOf(
            "{client} wants a website to list properties for sale and rent and collect enquiries.",
            "{client} needs a property listing website with search and agent contact details."
        ),
        pool = listOf(
            "Property listings with photo galleries", "Search and filter by location and price",
            "Property detail pages", "Enquiry form per property", "Agent profile pages",
            "Map for each listing", "Admin page to add or remove listings",
            "Featured properties on the home page"
        )
    ),
    SoftwareType(
        code = "W7", title = "E-commerce store", kind = OfferKind.WEBSITE,
        minNgn = 1_000_000L, maxNgn = 3_000_000L, allowCompany = true, allowIndividual = true,
        summaries = listOf(
            "{client} wants an online store to sell its products across the country.",
            "{client} needs an e-commerce website with cart, checkout and order tracking."
        ),
        pool = listOf(
            "Product catalogue with categories", "Shopping cart and checkout",
            "Online payment with Paystack or Flutterwave", "Order management page for the owner",
            "Discount codes", "Customer accounts", "Order confirmation emails",
            "Delivery fee by location"
        )
    ),
    SoftwareType(
        code = "W8", title = "Hotel / guesthouse website", kind = OfferKind.WEBSITE,
        minNgn = 900_000L, maxNgn = 2_000_000L, allowCompany = true, allowIndividual = false,
        summaries = listOf(
            "{client} wants a hotel website where guests can see rooms and request bookings.",
            "{client} needs a guesthouse website with room pages and a booking request form."
        ),
        pool = listOf(
            "Room pages with photos and prices", "Booking request form",
            "Availability calendar display", "Gallery and facilities page", "Guest reviews",
            "Location and directions", "Contact and WhatsApp button",
            "Admin page to update room prices"
        )
    ),
    SoftwareType(
        code = "A1", title = "Clinic booking system", kind = OfferKind.WEB_APP,
        minNgn = 1_200_000L, maxNgn = 3_000_000L, allowCompany = true, allowIndividual = false,
        summaries = listOf(
            "{client} is a clinic that wants patients to book appointments online.",
            "{client} needs a web system to manage appointments, doctors and patient visits."
        ),
        pool = listOf(
            "Online appointment booking", "Doctor schedules", "Patient records (basic)",
            "SMS or email reminders", "Admin dashboard", "Search for patients",
            "Daily appointment list", "Printable visit summary"
        )
    ),
    SoftwareType(
        code = "A2", title = "Inventory and POS web app", kind = OfferKind.WEB_APP,
        minNgn = 1_500_000L, maxNgn = 3_500_000L, allowCompany = true, allowIndividual = false,
        summaries = listOf(
            "{client} wants a web app to track stock and record sales in its shop.",
            "{client} needs a point-of-sale and inventory system for its stores."
        ),
        pool = listOf(
            "Product and stock management", "Sales screen with receipts", "Low-stock alerts",
            "Daily and monthly sales reports", "Staff accounts with roles", "Supplier records",
            "Works well on a phone browser", "Data export to Excel"
        )
    ),
    SoftwareType(
        code = "A3", title = "School management portal", kind = OfferKind.WEB_APP,
        minNgn = 2_000_000L, maxNgn = 4_500_000L, allowCompany = true, allowIndividual = false,
        summaries = listOf(
            "{client} wants a portal for results, attendance and fee records.",
            "{client} needs a school management system for teachers, students and parents."
        ),
        pool = listOf(
            "Student and class records", "Results entry and report cards", "Attendance tracking",
            "Fee records and receipts", "Parent login to view results", "Teacher accounts",
            "Announcements", "Printable reports"
        )
    ),
    SoftwareType(
        code = "A4", title = "Logistics tracking platform", kind = OfferKind.WEB_APP,
        minNgn = 2_500_000L, maxNgn = 5_000_000L, allowCompany = true, allowIndividual = false,
        summaries = listOf(
            "{client} is a logistics company that wants customers to track deliveries online.",
            "{client} needs a platform to manage dispatch riders, shipments and tracking."
        ),
        pool = listOf(
            "Shipment creation and tracking numbers", "Public tracking page",
            "Rider accounts and assignment", "Status updates (picked up, in transit, delivered)",
            "Admin dashboard", "Delivery proof (name and time)", "Customer notifications",
            "Monthly shipment reports"
        )
    ),
    SoftwareType(
        code = "A5", title = "Recruitment / job board platform", kind = OfferKind.WEB_APP,
        minNgn = 2_000_000L, maxNgn = 4_500_000L, allowCompany = true, allowIndividual = false,
        summaries = listOf(
            "{client} wants a job board where employers post jobs and candidates apply.",
            "{client} needs a recruitment platform with employer and candidate accounts."
        ),
        pool = listOf(
            "Employer accounts and job posting", "Candidate profiles and CV upload",
            "Job search and filters", "Application tracking", "Admin approval of jobs",
            "Email alerts", "Company pages", "Dashboard with applicant counts"
        )
    ),
    SoftwareType(
        code = "A6", title = "Fintech wallet web app", kind = OfferKind.WEB_APP,
        minNgn = 3_000_000L, maxNgn = 5_000_000L, allowCompany = true, allowIndividual = false,
        summaries = listOf(
            "{client} wants a wallet app for airtime, data and bill payments.",
            "{client} needs a payments web app where customers fund a wallet and buy services."
        ),
        pool = listOf(
            "User sign-up and wallet", "Wallet funding by bank transfer or card",
            "Airtime and data purchase", "Transaction history",
            "Admin dashboard with reports", "Referral bonus", "PIN protection",
            "Works as an installable web app (PWA)"
        )
    ),
    SoftwareType(
        code = "D1", title = "Delivery / errand Android app", kind = OfferKind.ANDROID_APP,
        minNgn = 2_500_000L, maxNgn = 5_000_000L, allowCompany = true, allowIndividual = false,
        summaries = listOf(
            "{client} wants an Android app for ordering deliveries and errands.",
            "{client} needs a delivery Android app with customer and rider screens."
        ),
        pool = listOf(
            "Customer sign-up and order screen", "Rider screen with assigned orders",
            "Live order status", "Price estimate by distance", "Order history",
            "Push notification style alerts inside the app", "Admin web page for orders",
            "Ratings after delivery"
        )
    ),
    SoftwareType(
        code = "D2", title = "Business management Android app", kind = OfferKind.ANDROID_APP,
        minNgn = 1_500_000L, maxNgn = 4_000_000L, allowCompany = true, allowIndividual = true,
        summaries = listOf(
            "{client} wants an Android app to record sales, expenses and print receipts.",
            "{client} needs a simple business app for daily sales and stock on the phone."
        ),
        pool = listOf(
            "Daily sales entry", "Expense tracking", "Stock list",
            "Printable or shareable receipts", "Daily and monthly summary", "Customer list",
            "Works offline", "Backup to a file"
        )
    ),
    SoftwareType(
        code = "D3", title = "E-learning Android app", kind = OfferKind.ANDROID_APP,
        minNgn = 2_000_000L, maxNgn = 4_500_000L, allowCompany = true, allowIndividual = true,
        summaries = listOf(
            "{client} wants an Android app to sell and deliver online courses.",
            "{client} needs a learning app with lessons, quizzes and certificates."
        ),
        pool = listOf(
            "Course list and lesson pages", "Video or text lessons", "Quizzes with scores",
            "Progress tracking", "Certificate at the end", "Student accounts",
            "Payment for premium courses", "Admin page to upload lessons"
        )
    )
)

/** The two choices for the hosting line and for the content line of every build proposal. */
internal val HOSTING_LINES: List<String> = listOf(
    "Client already has a domain and hosting.",
    "Please include domain and hosting setup (client pays the yearly fees)."
)

internal val CONTENT_LINES: List<String> = listOf(
    "Client will send the logo, text and photos.",
    "Please help with sample text and images where needed."
)

/** The two summaries used when a client buys the complete code. {type} is the lower-case type title. */
internal val CODE_SUMMARIES: List<String> = listOf(
    "{client} wants to buy the complete source code of a {type} built by Neribo Group, with a full handoff.",
    "{client} is asking to purchase the full source code and documentation of a {type} and take over its development."
)

// ---------------------------------------------------------------------------
// Countries and names
// ---------------------------------------------------------------------------

internal data class SoftwareCountry(
    val name: String,
    val currency: String,
    val phonePrefix: String,
    /** How many digits follow the prefix. */
    val phoneDigits: Int,
    /** The ending of company email addresses, for example "co.uk". */
    val emailTld: String,
    val legalSuffixes: List<String>,
    val firstNames: List<String>,
    val surnames: List<String>
)

internal const val NIGERIA = "Nigeria"

internal val FOREIGN_COUNTRIES: List<SoftwareCountry> = listOf(
    SoftwareCountry(
        name = "United Kingdom", currency = "GBP", phonePrefix = "+44 7", phoneDigits = 9,
        emailTld = "co.uk", legalSuffixes = listOf("Ltd", "Limited"),
        firstNames = listOf("Oliver", "Emma", "Harry", "Charlotte", "George", "Amelia", "Jack", "Grace", "Thomas", "Hannah"),
        surnames = listOf("Whitfield", "Hargreaves", "Pemberton", "Ashworth", "Thornton", "Fairburn", "Caldwell", "Winters", "Holloway", "Prescott")
    ),
    SoftwareCountry(
        name = "United States", currency = "USD", phonePrefix = "+1 ", phoneDigits = 10,
        emailTld = "com", legalSuffixes = listOf("LLC", "Inc."),
        firstNames = listOf("Michael", "Jessica", "David", "Ashley", "James", "Megan", "Daniel", "Lauren", "Ryan", "Natalie"),
        surnames = listOf("Brooks", "Carter", "Henderson", "Mitchell", "Sullivan", "Parker", "Reynolds", "Hayes", "Dawson", "Whitaker")
    ),
    SoftwareCountry(
        name = "Canada", currency = "CAD", phonePrefix = "+1 ", phoneDigits = 10,
        emailTld = "com", legalSuffixes = listOf("Inc.", "Ltd."),
        firstNames = listOf("Liam", "Olivia", "Noah", "Chloe", "Ethan", "Sophie", "Lucas", "Megan", "Mason", "Isabelle"),
        surnames = listOf("Tremblay", "Gagnon", "Campbell", "MacDonald", "Fraser", "Lavoie", "Bouchard", "Stewart", "Morin", "Sinclair")
    ),
    SoftwareCountry(
        name = "Germany", currency = "EUR", phonePrefix = "+49 15", phoneDigits = 9,
        emailTld = "de", legalSuffixes = listOf("GmbH", "UG"),
        firstNames = listOf("Lukas", "Anna", "Jonas", "Lena", "Felix", "Marie", "Maximilian", "Laura", "Tobias", "Julia"),
        surnames = listOf("Bergmann", "Vogel", "Hartmann", "Keller", "Lehmann", "Brandt", "Kruger", "Neumann", "Seidel", "Wagner")
    ),
    SoftwareCountry(
        name = "Ghana", currency = "GHS", phonePrefix = "+233 ", phoneDigits = 9,
        emailTld = "com.gh", legalSuffixes = listOf("Ltd", "Enterprise"),
        firstNames = listOf("Kwame", "Ama", "Kofi", "Abena", "Yaw", "Akosua", "Kwesi", "Efua", "Nana", "Adwoa"),
        surnames = listOf("Mensah", "Owusu", "Boateng", "Asante", "Appiah", "Quaye", "Darko", "Ofori", "Agyeman", "Tetteh")
    ),
    SoftwareCountry(
        name = "South Africa", currency = "ZAR", phonePrefix = "+27 ", phoneDigits = 9,
        emailTld = "co.za", legalSuffixes = listOf("(Pty) Ltd"),
        firstNames = listOf("Thabo", "Lindiwe", "Pieter", "Nomsa", "Johan", "Zanele", "Sipho", "Annelie", "Bongani", "Marelize"),
        surnames = listOf("Naidoo", "Van der Merwe", "Dlamini", "Botha", "Khumalo", "Pretorius", "Mokoena", "Du Plessis", "Ndlovu", "Steyn")
    ),
    SoftwareCountry(
        name = "United Arab Emirates", currency = "AED", phonePrefix = "+971 5", phoneDigits = 8,
        emailTld = "ae", legalSuffixes = listOf("FZE", "LLC"),
        firstNames = listOf("Omar", "Fatima", "Khalid", "Mariam", "Hassan", "Layla", "Rashid", "Noura", "Tariq", "Salma"),
        surnames = listOf("Al Mansoori", "Al Hashimi", "Al Farsi", "Al Suwaidi", "Al Nuaimi", "Al Kaabi", "Al Marri", "Al Zaabi", "Al Falasi", "Al Dhaheri")
    ),
    SoftwareCountry(
        name = "Kenya", currency = "KES", phonePrefix = "+254 7", phoneDigits = 8,
        emailTld = "co.ke", legalSuffixes = listOf("Ltd"),
        firstNames = listOf("Wanjiru", "Kamau", "Achieng", "Otieno", "Njeri", "Kiplagat", "Atieno", "Mwangi", "Chebet", "Kariuki"),
        surnames = listOf("Odhiambo", "Kimani", "Wekesa", "Mutua", "Njoroge", "Cheruiyot", "Omondi", "Karanja", "Wambui", "Maina")
    ),
    SoftwareCountry(
        name = "Australia", currency = "AUD", phonePrefix = "+61 4", phoneDigits = 8,
        emailTld = "com.au", legalSuffixes = listOf("Pty Ltd"),
        firstNames = listOf("Jack", "Matilda", "Cooper", "Isla", "Mitchell", "Ruby", "Hamish", "Zoe", "Lachlan", "Chloe"),
        surnames = listOf("Thompson", "Gallagher", "McKenzie", "Ridley", "Sutherland", "Hollis", "Kingsley", "Redfern", "Callaghan", "Barrett")
    )
)

internal val NIGERIAN_FIRST_NAMES: List<String> = listOf(
    "Adebayo", "Chidinma", "Ibrahim", "Aisha", "Emeka", "Folake", "Tunde", "Ngozi", "Musa",
    "Hauwa", "Osagie", "Efosa", "Ekaette", "Inyang", "Tamuno", "Preye", "Oluwaseun", "Chinedu",
    "Funmilayo", "Bashir", "Zainab", "Ifeanyi", "Blessing", "Seyi", "Kehinde", "Uche", "Amina",
    "Yusuf", "Itohan", "Etim", "Ebiere", "Damilola", "Nkechi", "Sani", "Omoyele", "Ifeoma"
)

internal val NIGERIAN_SURNAMES: List<String> = listOf(
    "Adeyemi", "Okafor", "Bello", "Abubakar", "Eze", "Balogun", "Nwosu", "Ogunleye", "Danjuma",
    "Omoregie", "Edet", "Ekpo", "Igwe", "Okonkwo", "Adewale", "Lawal", "Garba", "Uzor", "Ikpe",
    "Etuk", "Ogbonna", "Akpan", "Ibeh", "Tijani", "Salami", "Onyema", "Ogunbanjo", "Yakubu",
    "Idowu", "Oyelaran", "Ibiam", "Alabi", "Obi", "Amadi", "Sadiq", "Ekanem"
)

internal val NIGERIAN_COMPANY_PREFIXES: List<String> = listOf(
    "Greenfield", "Bluewave", "Crestline", "Sunrise", "Evergreen", "Goldcrest", "Harvest",
    "Lakeside", "Northgate", "Oakridge", "Pinnacle", "Riverstone", "Silverline", "Topaz",
    "Unity", "Victory", "Westbrook", "Zenith", "Royal Palm", "Delta Star", "Savanna",
    "Horizon", "Kingsway", "Mainland", "Prime Choice", "Brightway", "Fountain"
)

internal val NIGERIAN_COMPANY_SECTORS: List<String> = listOf(
    "Logistics", "Foods", "Schools", "Properties", "Healthcare", "Fashion", "Motors", "Agro",
    "Energy", "Hospitality", "Media", "Consulting", "Pharmacy", "Builders", "Tech Hub", "Travels",
    "Cosmetics", "Print House", "Farms", "Services"
)

internal val NIGERIAN_COMPANY_SUFFIXES: List<String> = listOf(
    "Ltd", "Limited", "Nigeria Ltd", "Ventures", "Enterprises", "Global Services", "& Sons", "Concepts"
)

internal val ENGLISH_COMPANY_PREFIXES: List<String> = listOf(
    "Harbor", "Summit", "Bright", "Cedar", "Falcon", "Maple", "Orchard", "Pioneer", "Clearwater",
    "Redwood", "Ironbridge", "Willow", "Granite", "Beacon", "Meadow", "Northstar", "Silverpine",
    "Brookside", "Oakland", "Crescent", "Fairview", "Highland", "Lakeview", "Stonebridge",
    "Windmill", "Copperfield"
)

internal val ENGLISH_COMPANY_SECTORS: List<String> = listOf(
    "Digital", "Logistics", "Foods", "Estates", "Health", "Learning", "Retail", "Travel", "Energy",
    "Media", "Consulting", "Builders", "Motors", "Farms", "Design", "Care", "Supplies",
    "Financial", "Events", "Fitness", "Studios"
)

/** Nigerian phone numbers start with one of these and have 11 digits in total. */
internal val NIGERIAN_PHONE_PREFIXES: List<String> = listOf("080", "081", "070", "090")
