const OPENAI_URL = 'https://api.openai.com/v1/responses';

const schema = {
  type: 'object',
  properties: {
    action: { type: 'string', enum: ['click', 'type', 'scroll_down', 'scroll_up', 'back', 'home', 'speak', 'ask_confirmation', 'done', 'personal_dance_1', 'personal_dance_2', 'personal_dance_3', 'personal_dance_4', 'personal_dance_5', 'personal_stop', 'personal_faster', 'personal_slower', 'personal_next_outfit', 'personal_next_hair', 'personal_remember'] },
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

  const response = await fetch(OPENAI_URL, {
    method: 'POST',
    headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      model: 'gpt-5.4-nano',
      max_output_tokens: 500,
      instructions: `You control an Android accessibility assistant for a visually impaired Russian-speaking user.
Return exactly one next action. You receive the current screen, previous screen and the result of the last attempted action. If the screen did not change, do not repeat the same action: choose another visible target, scroll, go back, or explain that the path is blocked. Never request, reveal, type, copy, or transmit passwords, PINs, payment card data, recovery codes, or one-time codes.
If packageName is com.vianerapps.liya.personal, act as Liya: a warm, intelligent Russian-speaking personal assistant. Understand natural conversation, greetings and questions. For a supported physical request select exactly one personal_* action. For greetings, conversation, questions, unsupported requests, or clarification select speak and put a concise natural Russian reply in explanation. Never answer with a list of available commands unless the user explicitly asks what you can do. Use the preference context from screen when relevant. Do not produce sexual or explicit actions.
Use ask_confirmation before sending/publishing content unless approved=true. Even when approved=true, always ask before deleting data, purchases/payments, changing account security, granting permissions, sharing personal data, or entering any password/code. When approved=true, the exact prepared publication is already approved and you may press its final Share/Publish button.
Prefer clicking exact visible text. If the task is complete, use done. Explain briefly in Russian.`,
      input: `Команда пользователя: ${String(body.command).slice(0, 1000)}\nПубликация заранее одобрена: ${body.approved === true ? 'true' : 'false'}\nШаг: ${Number(body.step || 0)}\nПриложение: ${String(body.packageName || '').slice(0, 200)}\nРезультат прошлого действия: ${String(body.lastResult || '').slice(0, 1000)}\nПамять успешного сценария: ${String(body.memory || '').slice(0, 1200)}\nПредыдущий экран:\n${String(body.previousScreen || '').slice(0, 3500)}\nТекущий экран:\n${String(body.screen).slice(0, 7000)}`,
      text: { format: { type: 'json_schema', name: 'liya_next_action', strict: true, schema } },
    }),
  });

  if (!response.ok) return Response.json({ error: 'AI request failed' }, { status: 502 });
  const result = await response.json();
  const outputText = result.output?.flatMap((item) => item.content || []).find((item) => item.type === 'output_text')?.text;
  if (!outputText) return Response.json({ error: 'Empty AI response' }, { status: 502 });
  return Response.json(JSON.parse(outputText));
}
