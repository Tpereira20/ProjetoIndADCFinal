package pt.unl.fct.di.apdc.firstwebapp.util;

public class ChangePwdData {
    public String password;
    public String newPassword;
    public String confirmation;

    public ChangePwdData() {
    }

    public ChangePwdData(String password, String newPassword,String confirmation) {
        this.password = password;
        this.newPassword = newPassword;
        this.confirmation = confirmation;
    }
}
