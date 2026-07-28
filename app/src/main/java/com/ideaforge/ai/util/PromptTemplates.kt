package com.ideaforge.ai.util

import com.ideaforge.ai.domain.model.PromptCategory
import com.ideaforge.ai.domain.model.PromptTemplate

object PromptTemplates {

    val templates = listOf(
        PromptTemplate(
            id = "business_1",
            title = "Invoice Generator",
            description = "Generate professional invoices for your clients",
            prompt = "Create a business invoice generator app with the following features:\n- Create, edit, and delete invoices\n- Add client details (name, address, email)\n- Add line items with quantities and prices\n- Auto-calculate subtotals, tax, and total\n- PDF export and sharing\n- Save invoice history\n- Professional invoice templates\n- Currency selection\n- Tax rate configuration",
            category = PromptCategory.BUSINESS,
            icon = "\uD83D\uDCB3"
        ),
        PromptTemplate(
            id = "business_2",
            title = "CRM App",
            description = "Manage your customer relationships",
            prompt = "Create a customer relationship management (CRM) app with:\n- Contact management with photo, phone, email\n- Lead tracking and pipeline\n- Activity logging (calls, meetings, notes)\n- Follow-up reminders\n- Search and filter contacts\n- Export contacts\n- Dashboard with key metrics\n- Categories and tags for contacts",
            category = PromptCategory.BUSINESS,
            icon = "\uD83D\uDC65"
        ),
        PromptTemplate(
            id = "education_1",
            title = "Flashcard Study App",
            description = "Create flashcards for studying",
            prompt = "Create a flashcard study app with:\n- Create, edit, and delete flashcard decks\n- Add front and back cards\n- Spaced repetition algorithm\n- Study mode with swipe gestures\n- Track study progress\n- Import/export decks\n- Timer for study sessions\n- Statistics and streaks\n- Categories for organizing decks",
            category = PromptCategory.EDUCATION,
            icon = "\uD83D\uDCDA"
        ),
        PromptTemplate(
            id = "education_2",
            title = "Quiz Builder",
            description = "Build and take quizzes",
            prompt = "Create a quiz builder app with:\n- Create multiple-choice quizzes\n- Add true/false and open-ended questions\n- Timer for quizzes\n- Score tracking and history\n- Randomize question order\n- Share quizzes\n- Categories and difficulty levels\n- Detailed results after completion\n- Leaderboard",
            category = PromptCategory.EDUCATION,
            icon = "\uD83E\uDDE0"
        ),
        PromptTemplate(
            id = "church_1",
            title = "Church Events App",
            description = "Manage church events and services",
            prompt = "Create a church events app with:\n- Event calendar with services, Bible studies, and special events\n- Push notifications for upcoming events\n- Event details with description, time, location\n- Share events\n- Volunteer sign-up\n- Prayer request submission\n- Sermon notes\n- Church announcements\n- Contact church leadership",
            category = PromptCategory.CHURCH,
            icon = "\u26EA"
        ),
        PromptTemplate(
            id = "church_2",
            title = "Bible Reading Plan",
            description = "Daily Bible reading and devotions",
            prompt = "Create a Bible reading plan app with:\n- Daily reading schedule\n- Multiple reading plans (through the Bible in a year, etc.)\n- Bookmark passages\n- Highlight and annotate verses\n- Reading streak tracking\n- Notes section\n- Share verses\n- Search functionality\n- Reading reminders",
            category = PromptCategory.CHURCH,
            icon = "\uD83D\uDCD6"
        ),
        PromptTemplate(
            id = "finance_1",
            title = "Budget Tracker",
            description = "Track income and expenses",
            prompt = "Create a personal budget tracker with:\n- Track income and expenses\n- Categorize transactions\n- Monthly budget goals\n- Visual charts and graphs\n- Expense trends analysis\n- Savings goals\n- Export reports as CSV\n- Recurring transactions\n- Currency conversion\n- Financial summary dashboard",
            category = PromptCategory.FINANCE,
            icon = "\uD83D\uDCB0"
        ),
        PromptTemplate(
            id = "finance_2",
            title = "Investment Tracker",
            description = "Track your investment portfolio",
            prompt = "Create an investment portfolio tracker with:\n- Track stocks, bonds, crypto, and mutual funds\n- Real-time price updates\n- Portfolio performance charts\n- Profit/loss calculations\n- Transaction history\n- Watchlist\n- News feed\n- Alerts for price changes\n- Portfolio diversification analysis",
            category = PromptCategory.FINANCE,
            icon = "\uD83D\uDCC8"
        ),
        PromptTemplate(
            id = "shopping_1",
            title = "Shopping List",
            description = "Smart shopping list with categories",
            prompt = "Create a smart shopping list app with:\n- Create multiple shopping lists\n- Add items with quantity and notes\n- Categories (groceries, household, clothing, etc.)\n- Check off purchased items\n- Share lists with family\n- Favorite items for quick add\n- Store section grouping\n- Price tracking\n- Purchase history\n- Suggestions based on history",
            category = PromptCategory.SHOPPING,
            icon = "\uD83D\uDED2"
        ),
        PromptTemplate(
            id = "shopping_2",
            title = "Price Comparison",
            description = "Compare prices across stores",
            prompt = "Create a price comparison app with:\n- Add products to compare\n- Barcode scanner\n- Price history charts\n- Store information\n- Deal alerts\n- Wishlist with price drops\n- Share deals\n- Categories\n- Search functionality\n- Best deals section",
            category = PromptCategory.SHOPPING,
            icon = "\uD83C\uDFF7"
        ),
        PromptTemplate(
            id = "health_1",
            title = "Fitness Tracker",
            description = "Track workouts and health goals",
            prompt = "Create a comprehensive fitness tracker with:\n- Workout logging (type, duration, sets, reps, weight)\n- Exercise library with instructions\n- Progress photos\n- Body measurements tracking\n- Water intake tracking\n- Sleep tracking\n- Step counter\n- Calorie calculator\n- Workout plans\n- Progress charts and statistics",
            category = PromptCategory.HEALTH,
            icon = "\uD83C\uDFCB"
        ),
        PromptTemplate(
            id = "health_2",
            title = "Meditation App",
            description = "Guided meditation and mindfulness",
            prompt = "Create a meditation and mindfulness app with:\n- Guided meditation sessions\n- Timer for meditation\n- Breathing exercises\n- Soundscapes (rain, ocean, forest, etc.)\n- Meditation streak tracking\n- Daily mindfulness reminders\n- Mood tracking\n- Journal entries\n- Progress statistics\n- Categories (stress relief, sleep, focus, etc.)",
            category = PromptCategory.HEALTH,
            icon = "\uD83E\uDDD8"
        ),
        PromptTemplate(
            id = "games_1",
            title = "Trivia Quiz Game",
            description = "Test your knowledge with trivia",
            prompt = "Create a trivia quiz game with:\n- Multiple categories (science, history, pop culture, etc.)\n- Multiple difficulty levels\n- Timer for each question\n- Score tracking\n- High scores leaderboard\n- Streak bonuses\n- Share results\n- Daily challenges\n- Hints system\n- Sound effects",
            category = PromptCategory.GAMES,
            icon = "\uD83C\uDFAE"
        ),
        PromptTemplate(
            id = "games_2",
            title = "Word Puzzle Game",
            description = "Word games and puzzles",
            prompt = "Create a word puzzle game with:\n- Multiple game modes (word search, crossword, anagram)\n- Daily puzzles\n- Difficulty levels\n- Timer and scoring\n- Hints\n- Achievement system\n- Statistics tracking\n- Multiple languages\n- Share puzzles\n- Offline play",
            category = PromptCategory.GAMES,
            icon = "\uD83C\uDFB2"
        ),
        PromptTemplate(
            id = "ai_1",
            title = "AI Chat Assistant",
            description = "AI-powered chat interface",
            prompt = "Create an AI chat assistant app with:\n- Clean chat interface\n- Text-to-speech for responses\n- Chat history\n- Multiple conversation threads\n- Copy and share responses\n- Typing indicators\n- Dark/light theme\n- Search through conversations\n- Export chat history\n- Quick prompts/suggestions",
            category = PromptCategory.AI,
            icon = "\uD83E\uDD16"
        ),
        PromptTemplate(
            id = "productivity_1",
            title = "Task Manager",
            description = "Organize tasks with priorities and deadlines",
            prompt = "Create a comprehensive task manager with:\n- Create, edit, and delete tasks\n- Priority levels (high, medium, low)\n- Due dates and reminders\n- Subtasks and checklists\n- Categories and tags\n- Calendar view\n- Kanban board view\n- Search and filter\n- Recurring tasks\n- Progress statistics",
            category = PromptCategory.PRODUCTIVITY,
            icon = "\u2705"
        ),
        PromptTemplate(
            id = "productivity_2",
            title = "Notes App",
            description = "Rich text notes with organization",
            prompt = "Create a notes app with:\n- Rich text editing\n- Folders and categories\n- Tags\n- Pin important notes\n- Search functionality\n- Checklist mode\n- Voice notes\n- Attach photos\n- Sort options\n- Export and share notes",
            category = PromptCategory.PRODUCTIVITY,
            icon = "\uD83D\uDCDD"
        ),
        PromptTemplate(
            id = "utilities_1",
            title = "Unit Converter",
            description = "Convert between different units",
            prompt = "Create a unit converter app with:\n- Length, weight, volume, temperature conversions\n- Custom unit support\n- Favorite conversions\n- History of conversions\n- Copy results\n- Reverse conversion\n- Multiple unit categories\n- Clean calculator-style input\n- Currency converter\n- Time zone converter",
            category = PromptCategory.UTILITIES,
            icon = "\u2696"
        ),
        PromptTemplate(
            id = "social_1",
            title = "Social Feed App",
            description = "Social media feed with posts",
            prompt = "Create a social feed app with:\n- Create posts with text and images\n- Like and comment on posts\n- User profiles\n- Follow/unfollow users\n- Feed with infinite scroll\n- Share posts\n- Direct messaging\n- Notifications\n- Search users and posts\n- Trending section",
            category = PromptCategory.SOCIAL_MEDIA,
            icon = "\uD83D\uDC64"
        ),
        PromptTemplate(
            id = "government_1",
            title = "Citizen Services App",
            description = "Government services portal",
            prompt = "Create a citizen services app with:\n- Report issues (potholes, streetlights, etc.)\n- Track report status\n- Public announcements\n- Emergency contacts\n- Government office locations\n- Document checklist\n- Appointment booking\n- Feedback system\n- FAQ section\n- Language selection",
            category = PromptCategory.GOVERNMENT,
            icon = "\uD83C\uDFDB"
        )
    )

    fun getTemplatesByCategory(category: PromptCategory): List<PromptTemplate> {
        return templates.filter { it.category == category }
    }

    fun searchTemplates(query: String): List<PromptTemplate> {
        val lowerQuery = query.lowercase()
        return templates.filter {
            it.title.lowercase().contains(lowerQuery) ||
            it.description.lowercase().contains(lowerQuery) ||
            it.prompt.lowercase().contains(lowerQuery)
        }
    }
}
