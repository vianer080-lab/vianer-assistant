export function GET() {
  return Response.json({
    ok: true,
    service: 'master-hub-youtube-oauth',
    configured: Boolean(
      process.env.YOUTUBE_CLIENT_ID &&
      process.env.YOUTUBE_CLIENT_SECRET &&
      process.env.YOUTUBE_REDIRECT_URI
    ),
  });
}
