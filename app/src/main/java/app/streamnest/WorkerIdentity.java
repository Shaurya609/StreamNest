package app.streamnest;

/** Never terminate a PID without matching both the app UID and expected worker name. */
final class WorkerIdentity {
  static boolean matches(String command, String status, String expected, int uid) {
    int zero=command.indexOf('\0');
    if(!expected.equals(zero<0?command:command.substring(0,zero)))return false;
    for(String line:status.split("\n")) if(line.startsWith("Uid:")) {
      String[] fields=line.substring(4).trim().split("\\s+");
      if(fields.length!=4)return false;
      for(String field:fields)try{if(Integer.parseInt(field)!=uid)return false;}catch(NumberFormatException e){return false;}
      return true;
    }
    return false;
  }
}
