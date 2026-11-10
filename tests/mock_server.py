#!/usr/bin/env python3
"""Local-only UI fixture, never use production credentials. No video supplied."""
import asyncio
from aiohttp import web
async def login(request):
    form=await request.post()
    if form.get('mail')!='demo@example.test' or form.get('pass')!='demo':
        return web.json_response({'ok':False})
    return web.json_response({'$user':{'auth_token':'test-token','ke':'test-group','uid':'demo'}})
async def monitors(request):
    return web.json_response([{'mid':str(i),'name':name,'mode':'record','details':{'stream_type':'mp4','stream_vcodec':'copy','archivePreviewMonitor':'low-'+str(i),'input_maps':[]}} for i,name in enumerate(['Крыльцо','Двор','Детская площадка','Холл','Третий этаж','Задний двор','Бульвар','Четвёртый этаж'])])
async def videos(request):return web.json_response({'videos':[],'total':0})
async def websocket(request):
    ws=web.WebSocketResponse();await ws.prepare(request)
    await ws.send_str('0{"sid":"test","upgrades":[],"pingInterval":25000,"pingTimeout":20000}')
    async for msg in ws:
        if msg.type!=web.WSMsgType.TEXT:continue
        if msg.data=='40':await ws.send_str('40{"sid":"test"}')
        elif msg.data.startswith('42'):
            import json
            event=json.loads(msg.data[2:]);data=event[1]
            if data.get('f')=='init':await ws.send_str('42["f",{"f":"init_success"}]')
            elif data.get('ff')=='watch_on':await ws.send_str('42'+json.dumps(['f',{'f':'monitor_watch_on','id':data['id']}]))
        elif msg.data=='2':await ws.send_str('3')
    return ws
app=web.Application();app.router.add_post('/',login);app.router.add_get('/socket.io/',websocket);app.router.add_get('/test-token/monitor/test-group',monitors);app.router.add_get('/test-token/videos/test-group/{camera}',videos)
web.run_app(app,host='127.0.0.1',port=18765,access_log=None)
