/* 仅在本机提供静态原型；无数据库、外部网络或业务API。 */
'use strict';
const http=require('node:http'),fs=require('node:fs'),path=require('node:path');
const root=__dirname,preferred=Number(process.env.FASHION_PROTOTYPE_PORT||8768);
const types={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.png':'image/png','.jpg':'image/jpeg','.json':'application/json; charset=utf-8','.md':'text/plain; charset=utf-8','.svg':'image/svg+xml'};
const server=http.createServer((req,res)=>{try{if(!['GET','HEAD'].includes(req.method)){res.writeHead(405);return res.end();}const pathname=decodeURIComponent(new URL(req.url,'http://127.0.0.1').pathname),file=path.resolve(root,'.'+(pathname==='/'?'/index.html':pathname));if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);return res.end('Not found');}res.writeHead(200,{'Content-Type':types[path.extname(file)]||'application/octet-stream','Cache-Control':'no-cache','X-Content-Type-Options':'nosniff'});if(req.method==='HEAD')res.end();else fs.createReadStream(file).pipe(res);}catch{res.writeHead(400);res.end('Bad request');}});
server.on('error',err=>{if(err.code==='EADDRINUSE'){console.error('端口 '+preferred+' 已被占用。可设置 FASHION_PROTOTYPE_PORT 后重试。');process.exitCode=1;}else throw err;});
server.listen(preferred,'127.0.0.1',()=>console.log('织选原型已就绪：http://127.0.0.1:'+preferred+'\n仅本机可访问；停止此进程即可关闭服务。'));
for(const event of ['SIGINT','SIGTERM'])process.on(event,()=>server.close(()=>process.exit(0)));
