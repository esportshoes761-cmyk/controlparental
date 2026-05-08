const express = require('express');
const cors = require('cors');
const http = require('http');
const WebSocket = require('ws');

const app = express();
app.use(cors());
app.use(express.json());

const devices = {};
const listeners = new Set();

function broadcast(type, payload) {
  const message = JSON.stringify({ type, payload });
  listeners.forEach(ws => {
    if (ws.readyState === WebSocket.OPEN) {
      ws.send(message);
    }
  });
}

app.post('/api/register', (req, res) => {
  const { deviceId, role, name, platform } = req.body;
  if (!deviceId) return res.status(400).json({ error: 'deviceId es requerido' });
  devices[deviceId] = { deviceId, role, name, platform, updatedAt: new Date().toISOString() };
  broadcast('deviceRegistered', devices[deviceId]);
  return res.status(200).end();
});

app.post('/api/notification', (req, res) => {
  const notification = req.body;
  if (!notification || !notification.deviceId) return res.status(400).json({ error: 'Payload inválido' });
  devices[notification.deviceId] = {
    ...(devices[notification.deviceId] || {}),
    lastNotification: notification,
    updatedAt: new Date().toISOString()
  };
  broadcast('notification', notification);
  return res.status(200).end();
});

app.post('/api/contacts', (req, res) => {
  const contacts = req.body;
  if (!contacts || !contacts.deviceId) return res.status(400).json({ error: 'Payload inválido' });
  devices[contacts.deviceId] = {
    ...(devices[contacts.deviceId] || {}),
    lastContacts: contacts,
    updatedAt: new Date().toISOString()
  };
  broadcast('contacts', contacts);
  return res.status(200).end();
});

app.post('/api/screen', (req, res) => {
  const screen = req.body;
  if (!screen || !screen.deviceId) return res.status(400).json({ error: 'Payload inválido' });
  devices[screen.deviceId] = {
    ...(devices[screen.deviceId] || {}),
    lastScreen: { timestamp: screen.timestamp },
    updatedAt: new Date().toISOString()
  };
  broadcast('screen', { deviceId: screen.deviceId, timestamp: screen.timestamp });
  return res.status(200).end();
});

app.get('/api/ping', (req, res) => {
  res.json({ status: 'ok' });
});

app.get('/api/devices', (req, res) => {
  res.json(Object.values(devices));
});

const server = http.createServer(app);
const wss = new WebSocket.Server({ server, path: '/updates' });

wss.on('connection', (ws) => {
  listeners.add(ws);
  ws.on('close', () => {
    listeners.delete(ws);
  });
  ws.on('message', (message) => {
    try {
      const parsed = JSON.parse(message.toString());
      if (parsed.action === 'subscribe') {
        ws.send(JSON.stringify({ type: 'subscribed', payload: { status: 'ok' } }));
      }
    } catch (err) {
      console.error('Mensaje socket no válido', err);
    }
  });
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  console.log(`Backend de Parental Control escuchando en http://localhost:${PORT}`);
});
