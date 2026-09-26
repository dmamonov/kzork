# Kzork

A browser text adventure powered by a local Ktor server.
The world, actors, objects, turn records, and saved chat are Markdown files.
The browser is one continuous story with commands, images, and audio.

## Run

Install Java 21, then run from this repository:

```sh
./play.sh --demo
```

The launcher builds the application, starts it on localhost:8080, and opens your
default browser.
Click **Begin adventure** to enable audio and enter the ice dungeon.
Use the command box to explore, or **Guide plays** to watch the offline guide.
Pause lets the current turn finish, then returns control to you.

Try `north`, `west`, `look`, `take ice pick`, or `talk to keeper`.
The offline demo supports the authored ice dungeon and a limited command set.
Its automated guide is deterministic, not a language model.

## Live AI play

Put these settings in the ignored `.env` file or export them in your shell:

```dotenv
OPENAI_API_KEY=your-api-key
OPENAI_MODEL=gpt-4.1-mini
```

```sh
./play.sh --ai
```

The game master, invited actors, state updater, and optional player agent make
separate AI calls.
**Agent plays** uses only the public scene and transcript to choose commands.
It cannot read hidden rules, walkthroughs, or world files.

Without an explicit mode, a configured API key selects AI mode.
Without a key, the app selects the labeled offline demo.
The key stays on the server and is never sent to the browser.
AI mode sends the adventure context to OpenAI and uses your API account.

Each agent call allows at most ten responses and 12,000 output tokens per
response.
The default process limit is 300 API requests.
Use `--max-requests 100` to reduce it.
Automatic play also has a turn limit chosen in the UI and stops on victory,
failure, or a pause request.

## Saving and resuming

Every launch creates an independent playthrough under `.sessions/`.
The authored files under `games/` remain unchanged.
The terminal prints the saved adventure's directory.

```sh
./play.sh --demo --resume .sessions/your-session
```

Resume with the same mode used to start that session.
Refreshing the browser restores the transcript without restarting the world.
Automatic play never starts by itself after a reload of the server.

A resolved turn whose updater failed can be continued with **Retry pending
update**.
A process crash during a filesystem batch may require the recovery procedure in
[the tools documentation](tools/README.md).

## Options

- `--game PATH`: choose a different authored game in AI mode.
- `--resume PATH`: reopen a saved adventure.
- `--port 8081`: select another local port.
- `--no-browser`: start the server without opening a browser.
- `--max-requests N`: bound API requests for the server process.
- `--help`: show launch options.

Stop the server with Ctrl+C.
A new launch without `--resume` begins a new adventure.

## Presentation

The chat appends player commands, resolved outcomes, and location entries.
Reading old messages does not force the view back down.
**New events** jumps to the latest messages.

Music, effects, and narration have independent volume controls and a global
mute.
Submitting a command interrupts narration.
Messages can be replayed individually.
Browser speech supplies narration when a recorded voice asset is unavailable.
Locally synthesized ambient tones and sound effects work without API access.

Ready assets referenced by game or cell metadata are used when available.
Otherwise the ice dungeon uses procedural SVG illustrations and ambient tones.
These are working local fallbacks, not generated photographic artwork or
recorded music.
Runtime image generation and remote text-to-speech production are not included.

## Development

```sh
./gradlew check
./gradlew :app:installDist
./gradlew :tools:formatMarkdown
```

The build uses Java 21 throughout.
All Markdown source uses at most 80 characters per line and a new line after
each prose sentence.

Modules:

- `model`: document structures and Markdown formatting.
- `tools`: bounded filesystem tools and formal state derivation.
- `app`: Ktor server, agent orchestration, sessions, and browser UI.
- `utils`: original starter utilities; not required by the game runtime.

Tests use temporary game copies and fake AI responses.
They cover the complete dungeon, automated play, turn recovery, saved
transcripts, role orchestration, and HTTP access boundaries.
No test needs an API key or makes paid model calls.

The API client follows the official [Responses function-calling guide][api].
Server tests use Ktor's [test application support][ktor].

[api]: https://developers.openai.com/api/docs/guides/function-calling
[ktor]: https://ktor.io/docs/server-testing.html
