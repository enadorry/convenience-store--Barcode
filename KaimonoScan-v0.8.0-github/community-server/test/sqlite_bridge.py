import sqlite3,sys,json
request=json.load(sys.stdin)
connection=sqlite3.connect(sys.argv[1]);connection.row_factory=sqlite3.Row
connection.execute('PRAGMA foreign_keys=ON')
if 'schema' in request:
 connection.executescript(request['schema']);connection.commit();print('{}')
else:
 try:
  connection.execute('BEGIN')
  results=[]
  for statement in request['statements']:
   before=connection.total_changes
   cursor=connection.execute(statement['sql'],statement.get('args',[]))
   rows=[dict(row) for row in cursor.fetchall()] if cursor.description else []
   results.append({'success':True,'results':rows,'meta':{'changes':connection.total_changes-before}})
  connection.commit();print(json.dumps(results))
 except Exception:
  connection.rollback();raise
finally_close = connection.close()
