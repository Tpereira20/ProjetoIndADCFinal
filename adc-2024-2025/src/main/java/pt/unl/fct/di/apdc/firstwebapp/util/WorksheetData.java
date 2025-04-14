package pt.unl.fct.di.apdc.firstwebapp.util;

public class WorksheetData {
    public String workReference;
    public String workDescription;
    public String targetType;
    public String awardStatus;
    public String awardDate;
    public String estimatedStartDate;
    public String estimatedCompletionDate;
    public String entityAccount;
    public String awardingEntity;
    public String companyTIN;
    public String workStatus;
    public String comments;

    public WorksheetData() {}

    public WorksheetData(String workReference, String workDescription, String targetType, String awardStatus,
                         String awardDate, String estimatedCompletionDate, String estimatedStartDate, String entityAccount,
                         String awardingEntity, String companyTIN, String workStatus, String comments) {
        this.workReference = workReference;
        this.workDescription = workDescription;
        this.targetType = targetType;
        this.awardStatus = awardStatus;
        this.awardDate = awardDate;
        this.estimatedCompletionDate = estimatedCompletionDate;
        this.estimatedStartDate = estimatedStartDate;
        this.entityAccount = entityAccount;
        this.awardingEntity = awardingEntity;
        this.companyTIN = companyTIN;
        this.workStatus = workStatus;
        this.comments = comments;
    }

    public boolean isValid() {
        return workReference != null && !workReference.isEmpty() &&
                workDescription != null && !workDescription.isEmpty() &&
                (targetType != null && (targetType.equalsIgnoreCase("Pública") || targetType.equalsIgnoreCase("Privada"))) &&
                (awardStatus != null && (awardStatus.equalsIgnoreCase("ADJUDICADO") || awardStatus.equalsIgnoreCase("NAO_ADJUDICADO")));
    }

    public boolean isValidAwarded() {
        return awardDate != null && !awardDate.isEmpty() &&
                estimatedStartDate != null && !estimatedStartDate.isEmpty() &&
                estimatedCompletionDate != null && !estimatedCompletionDate.isEmpty() &&
                entityAccount != null && !entityAccount.isEmpty() &&
                awardingEntity != null && !awardingEntity.isEmpty() &&
                companyTIN != null && !companyTIN.isEmpty() &&
                workStatus != null && (workStatus.equalsIgnoreCase("NAO_INICIADO") || workStatus.equalsIgnoreCase("EM CURSO")
                || workStatus.equalsIgnoreCase("CONCLUIDO")) &&
                comments != null && !comments.isEmpty();
    }
}
