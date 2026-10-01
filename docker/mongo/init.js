// DEMO ONLY: broad roles so Vigil can run serverStatus and usersInfo(forAllDBs).
// README must call this out and describe the least-privilege custom role for real use.
db.getSiblingDB('admin').createUser({
  user: 'vigil_monitor',
  pwd: 'monpw',
  roles: [
    { role: 'clusterMonitor', db: 'admin' },
    { role: 'readAnyDatabase', db: 'admin' },
    { role: 'userAdminAnyDatabase', db: 'admin' }
  ]
});
db.getSiblingDB('vigil_demo').createUser({
  user: 'app_rw', pwd: 'apppw', roles: [{ role: 'readWrite', db: 'vigil_demo' }]
});
db.getSiblingDB('vigil_demo').events.insertMany(
  Array.from({ length: 1000 }, (_, i) => ({ n: i, kind: 'demo' }))
);
