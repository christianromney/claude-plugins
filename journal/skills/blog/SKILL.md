---
name: blog
description: Write and publish a blog post to basic-memory. Use when the user wants to write a post about a technical topic, lesson learned, debugging story, or working pattern — especially related to AI-assisted development.
user_invocable: true
---

# Journal: Blog

Write and publish a new blog post to the `engineering/artificial-intelligence/blog`
collection in basic-memory.

## Invocation

The user triggers this with `/journal:blog` followed by a topic or description.
If no topic is given, ask what they'd like to write about.

## Steps

1. **Understand the topic.** Confirm your understanding of what the post is about.
   If the user gave a description, use it. If it's vague, ask one clarifying question.

2. **Draft the post.** Write the full post following the format below and the
   voice guidelines in @references/voice.md. Show the complete draft to the user.

3. **Get approval.** Ask if they want to publish as-is, revise, or discard.
   Iterate until approved.

4. **Publish to basic-memory.** Use `mcp__basic-memory__write_note`:
   - `title`: the short title only — no subtitle. This becomes the filename slug.
     Use 2–4 words, kebab-cased by the tool. Example: "Two Kinds of Memory",
     not "Two Kinds of Memory: Organizing Claude Code's Context".
   - `directory`: `engineering/artificial-intelligence/blog`
   - `tags`: 3–6 relevant lowercase tags
   - `content`: the full post content as described below

5. **Confirm.** Show the user the permalink of the published note.

## Post Format

### Front Matter

`write_note` generates front matter from the `title` and `tags` parameters.
Do not duplicate front matter in the `content` field.

### Content Structure

```
# Short Title: Descriptive Subtitle

> **AI Disclosure**: Claude Sonnet 4.6 co-authored this document.
> **Last Review**: Unreviewed
> **[Version History](#version-history)**

[Opening paragraph]

## Section Heading

[Body paragraphs]

...

---

## Version History

| Date | Description | Changes | Review |
|------|-------------|---------|--------|
| YYYY-MMM-DD | Initial draft | +N lines | Unreviewed |
```

### Title and Subtitle

- The H1 uses the full `# Short Title: Descriptive Subtitle` form.
- The short title (before the colon) is catchy and memorable. 2–4 words.
- The subtitle (after the colon) is a precise descriptive phrase.
- Only the short title goes in `write_note`'s `title` parameter.

### Opening Hook

The first paragraph must earn the reader's attention:
- A specific observation or moment that opens the story
- A concrete detail that immediately grounds the piece
- A tension or contradiction worth resolving

Do not open with background or definitions. Get to the point.

### Sections

- Use `##` headings. 3–6 sections is typical.
- Each section title names what happens in it, not just the topic.
  "Reading the Evidence" is better than "Evidence".
- Sections should build on each other — the post should feel like it moves.

### Prose Style

- Paragraphs over bullets. Use bullets only when genuinely enumerating
  parallel items — not as a substitute for writing sentences.
- Do not force a conclusion. End on a concrete note if no reflection arises.
- Aim for 500–800 words.

### Closing

End the body with a horizontal rule (`---`) before the Version History section.

### AI Disclosure and Version History

Follow `@reference/disclosure.md`. Role is almost always `co-authored` for blog posts.

## Important

- Always show the full draft before publishing. Never auto-publish.
- One post per invocation.
