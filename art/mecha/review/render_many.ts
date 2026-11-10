// Calls bbmodel_render many times over one MCP session. usage: bun run render_many.ts <requests.json>
// requests: [{file, clip?, time?, view, width, height, output, lighting?, orthographic?}]
import { Client } from "C:/Users/alvin/AppData/Local/Programs/blockbench-mcp/node_modules/@modelcontextprotocol/sdk/dist/esm/client/index.js";
import { StdioClientTransport } from "C:/Users/alvin/AppData/Local/Programs/blockbench-mcp/node_modules/@modelcontextprotocol/sdk/dist/esm/client/stdio.js";
const [reqPath] = process.argv.slice(2);
const reqs = JSON.parse(await Bun.file(reqPath).text());
const client = new Client({ name: "render-many", version: "1.0.0" });
await client.connect(new StdioClientTransport({ command: "bun", args: ["run", "C:/Users/alvin/AppData/Local/Programs/blockbench-mcp/node_modules/blockbench-mcp/headless/index.ts", "--root", "C:/Homework/Sessions/Japan/head-in-a-jar"], stderr: "ignore" }));
for (const r of reqs) {
  const tool = r.tool ?? "bbmodel_render";
  const args = { ...r }; delete args.tool;
  const res: any = await client.callTool({ name: tool, arguments: { overwrite: true, ...args } });
  if (res.isError) console.log("ERR", r.output, res.content[0].text.slice(0, 300));
  else console.log("ok", r.output);
}
await client.close();
process.exit(0);
