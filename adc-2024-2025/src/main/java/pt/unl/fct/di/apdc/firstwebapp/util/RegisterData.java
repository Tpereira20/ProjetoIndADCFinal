package pt.unl.fct.di.apdc.firstwebapp.util;

public class RegisterData {

    public String email;
    public String username;
    public String password;
    public String confirmation;
    public String name;
    public String phoneNumber;
    public String privacy;

    public String ccNumber;
    public String role;
    public String nif;
    public String employer;
    public String job;
    public String address;
    public String employerNif;
    public String accountState;


    public RegisterData() {

    }

    public RegisterData(String email, String username, String name, String phoneNumber, String password, String confirmation, String privacy, String role,
                        String nif, String employer, String job, String address, String employerNif, String accountState) {
        this.username = username;
        this.password = password;
        this.confirmation = confirmation;
        this.email = email;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.privacy = privacy;
        this.role = role;
        this.nif = nif;
        this.employer = employer;
        this.job = job;
        this.address = address;
        this.employerNif = employerNif;
        this.accountState = accountState;
    }

    private boolean nonEmptyOrBlankField(String field) {
        return field != null && !field.isBlank();
    }

    public boolean validRegistration() {
        return nonEmptyOrBlankField(username) &&
                nonEmptyOrBlankField(password) &&
                nonEmptyOrBlankField(email) &&
                nonEmptyOrBlankField(name) &&
                email.contains("@") &&
                password.equals(confirmation) &&
                nonEmptyOrBlankField(phoneNumber) &&
                nonEmptyOrBlankField(privacy);
    }
}
