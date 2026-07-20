# AI Usage Policy

This project enforces a policy on the use of Artificial Intelligence (AI) tools in development. The core rules of this policy are based on the [GregTech CEu AI Policy](https://github.com/GregTechCEu/GregTech/blob/master/AI_POLICY.md), with custom adaptations for our workflow.

---

## 1. Our Philosophy

We strive to use state-of-the-art development tools to accelerate innovation, optimize performance, and automate routine tasks. Artificial Intelligence (large language models, code assistants) is applied in our workflow strictly as an **assistant tool to boost productivity**, but not as an autonomous author.

Every line of code, architectural decision, and feature integration is ultimately guided, verified, refined, and maintained exclusively by human developers.

---

## 2. Core Rules for AI Usage

* **All AI usage in any form must be disclosed.** You must state the tool you used (e.g., Claude Code, Cursor, Antigravity, GitHub Copilot) along with the extent that the work was AI-assisted in your Pull Request descriptions.
* **The human-in-the-loop must fully understand all code.** If you cannot explain what your changes do and how they interact with the greater system without the aid of AI tools, do not contribute those changes.
* **Issues and discussions can use AI assistance but must have a full human-in-the-loop.** This means that any content generated with AI must have been reviewed *and edited* by a human before submission. The author must trim down overly verbose content.
* **No AI-generated media is allowed (art, images, videos, audio, etc.).** Text and code are the only acceptable AI-generated content, per the other rules in this policy.
* **Bad AI drivers may be blocked.** Contributors who submit low-quality AI-generated contributions (slop) without review may be blocked. If you want to learn, do not rely blindly on AI, and we will gladly help you.

---

## 3. Boundaries and Areas of AI Application

We clearly outline where and how AI may be integrated into our workflow:

### Code Assistance and Optimization
* **Generating boilerplate code:** Creating repetitive data structures, standard methods, or basic file outlines.
* **Refactoring and optimization:** Analyzing human-written algorithms to find more efficient solutions, micro-optimizations, or updated syntax.
* **Debugging:** Helping isolate rare edge cases, interpreting complex error logs, or finding memory leaks.

### System Design and Architecture
* **Validation and brainstorming:** Using AI as a sounding board to verify the viability of architectural ideas before writing code.
* **API Design:** Speeding up data schema generation, API documentation, and configuration files based on human-defined parameters.

### Text and Localization
* **Documentation:** Assisting in writing clear code comments (docstrings) and technical manuals.
* **Translation:** Adjusting UI texts into different languages for better readability.

---

## 4. What AI Does NOT Do (Our Hard Rules)

* **No blind copy-pasting:** No AI-generated code snippet enters the project without strict manual review, testing, and adaptation to the overall architecture.
* **Security & Privacy:** We do not upload confidential user data or critical security elements to closed AI models.
* **Direction set by humans:** AI does not make decisions on what to develop, which features to add, or how the project should evolve. The project ideology and vision belong entirely to the team.
* **No AI-generated media:** No AI-generated graphical or audio assets (textures, sounds, models) are allowed. AI is only permitted for text and code under the rules of this policy.

---

## 5. Guidelines for Developers and Contributors

1. **Tool Disclosure:** Add a short note in your PR description: "Assisted by [Tool Name] for boilerplate/refactoring."
2. **Reviewing Code:** Always run and compile the code locally, and write/run tests before submitting.
3. **No Slop:** Do not copy-paste code suggestions from LLMs that contain obvious bugs, placeholder comments, or unneeded logic.
4. **Filtering and Editing:** AI outputs are often overly verbose. Review, trim, and format your text and code before submitting.

---

> **Summary:** We do not replace human effort with automation — we arm our developers with the best tools to make the project better and faster for everyone.
