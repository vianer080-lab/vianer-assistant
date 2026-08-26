const OPENAI_URL = 'https://api.openai.com/v1/responses';

const schema = {
  type: 'object',
  properties: {
    action: { type: 'string', enum: ['click', 'type', 'scroll_down', 'scroll_up', 'back', 'home', 'speak', 'ask_confirmation', 'done', 'personal_dance_1', 'personal_dance_2', 'personal_dance_3', 'personal_dance_4', 'personal_dance_5', 'personal_stop', 'personal_faster', 'personal_slower', 'personal_next_outfit', 'personal_swimsuit', 'personal_next_hair', 'personal_remember'] },
    target: { type: 'string' },
    text: { type: 'string' },
    explanation: { type: 'string' },
  },
  required: ['action', 'target', 'text', 'explanation'],
  additionalProperties: false,
};

export function GET() {
  return Response.json({
    ok: true,
    service: 'liya-screen-assistant',
    configured: Boolean(process.env.OPENAI_API_KEY && process.env.LIYA_DEVICE_TOKEN),
  });
}

export async function POST(request) {
  const apiKey = process.env.OPENAI_API_KEY;
  const deviceToken = process.env.LIYA_DEVICE_TOKEN;
  if (!apiKey || !deviceToken) return Response.json({ error: 'Server is not configured' }, { status: 503 });
  if (request.headers.get('authorization') !== `Bearer ${deviceToken}`) {
    return Response.json({ error: 'Unauthorized' }, { status: 401 });
  }

  const body = await request.json().catch(() => null);
  if (!body?.command || !body?.screen) return Response.json({ error: 'Invalid request' }, { status: 400 });
  const personalMode = String(body.packageName || '') === 'com.vianerapps.liya.personal';

  const response = await fetch(OPENAI_URL, {
    method: 'POST',
    headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      model: personalMode ? 'gpt-5.4-mini' : 'gpt-5.4-nano',
      reasoning: { effort: 'none' },
      max_output_tokens: personalMode ? 360 : 220,
      instructions: `You control an Android accessibility assistant for a visually impaired Russian-speaking user.
Return exactly one next action. You receive the current screen, previous screen and the result of the last attempted action. If the screen did not change, do not repeat the same action: choose another visible target, scroll, go back, or explain that the path is blocked. Never request, reveal, type, copy, or transmit passwords, PINs, payment card data, recovery codes, or one-time codes.
Do not use speak or done merely to describe what should be tried next. If a safe relevant button is visible, click it yourself. On the Android system share chooser, when the approved task requests Instagram, click the visible target containing Instagram (including «Instagram», «Лента Instagram» or «Ваша история» as appropriate). After opening Instagram, continue through visible «Далее» buttons and use the final «Поделиться» only for approved=true. Use done only after the requested result is visibly complete, such as the new post appearing in the profile/feed or a clear success message.
If packageName is com.vianerapps.liya.personal, this is a normal spoken conversation, not a screen-control task. Act as Liya, a warm, intelligent Russian-speaking woman speaking directly to the user. For any understandable question, select speak and put the complete direct answer in explanation. Answer in first person when asked about yourself. Never narrate the request, never say «пользователь просит», never mention instructions or policies, never demand polite wording, and never ask the user to repeat or clarify an understandable question. Ask one short clarification only when the meaning is genuinely impossible to determine. «Без откровенного контента» means tasteful adult clothing and behavior without nudity, visible intimate areas, explicit sexual acts or pornographic presentation; explain this directly if asked. For a supported physical request select exactly one personal_* action. For a swimsuit, beach outfit or «купа́льник» request select personal_swimsuit. Never answer with a list of commands unless explicitly asked what you can do. Do not produce sexual or explicit actions.
Use ask_confirmation before sending/publishing content unless approved=true. Even when approved=true, always ask before deleting data, purchases/payments, changing account security, granting permissions, sharing personal data, or entering any password/code. When approved=true, the exact prepared publication is already approved: do not ask again and press its final Share/Publish button.
Prefer clicking exact visible text. If the task is complete, use done. Explain briefly in Russian.`,
      input: `Команда пользователя: ${String(body.command).slice(0, 700)}\nПубликация заранее одобрена: ${body.approved === true ? 'true' : 'false'}\nШаг: ${Number(body.step || 0)}\nПриложение: ${String(body.packageName || '').slice(0, 160)}\nРезультат прошлого действия: ${String(body.lastResult || '').slice(0, 650)}\nПамять успешного сценария: ${String(body.memory || '').slice(0, 600)}\nПредыдущий экран:\n${String(body.previousScreen || '').slice(0, 2200)}\nТекущий экран:\n${String(body.screen).slice(0, 4500)}`,
      text: { format: { type: 'json_schema', name: 'liya_next_action', strict: true, schema } },
    }),
  });

  if (!response.ok) return Response.json({ error: 'AI request failed' }, { status: 502 });
  const result = await response.json();
  const outputText = result.output?.flatMap((item) => item.content || []).find((item) => item.type === 'output_text')?.text;
  if (!outputText) return Response.json({ error: 'Empty AI response' }, { status: 502 });
  return Response.json(JSON.parse(outputText));
}
