---
name: slack
description: Post a journal entry to the #christian-ai-journal Slack channel. Use when the user wants to share something interesting from a session — a lesson learned, a debugging story, a useful pattern, or a reflection on working with AI.
user_invocable: true
---

# Journal: Slack

Post a journal entry to the `#christian-ai-journal` Slack channel — a personal
blog-style feed for sharing interesting moments from working with Claude.

## Invocation

The user triggers this with `/journal:slack` followed by a topic or description.
If no topic is given, ask what they'd like to post about.

## Steps

1. **Understand the topic.** Confirm your understanding. If vague, ask one
   clarifying question.

2. **Draft the post.** Write the full post following the format below and the
   voice guidelines in @references/voice.md. Show the complete draft to the user.

3. **Get approval.** Ask if they want to post as-is, edit, or discard.

4. **Post to Slack.** Use `mcp__plugin_slack_slack__slack_send_message`:
   - `channel_name`: `christian-ai-journal`
   - `text`: the approved post content

5. **Confirm.** Show the user that the post was sent successfully.

## Post Format

### Markdown

Use Slack mrkdwn, not GitHub-flavored markdown:
- Bold: `*text*`
- Italic: `_text_`
- Code: backticks
- Links: `<url|text>`

### Structure

- First line: a brief, punchy title in bold (`*Title*`)
- Body: 3–8 short paragraphs separated by blank lines
- No headers — Slack is not a blog platform; respect the scroll

### Length

Short. Say one thing well. If there are multiple stories, pick the best one.

## Error Handling

- If the Slack MCP fails, show the error and ask the user how to proceed.
- Do not retry automatically.

## Important

- Always show the draft before posting. Never auto-post.
- One post per invocation.
