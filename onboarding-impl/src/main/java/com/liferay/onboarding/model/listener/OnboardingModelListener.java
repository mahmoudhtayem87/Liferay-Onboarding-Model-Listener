package com.liferay.onboarding.model.listener;

import com.liferay.counter.kernel.service.CounterLocalService;
import com.liferay.dynamic.data.mapping.model.DDMFormInstanceRecord;
import com.liferay.dynamic.data.mapping.service.DDMFormInstanceRecordLocalService;
import com.liferay.dynamic.data.mapping.storage.DDMFormFieldValue;
import com.liferay.dynamic.data.mapping.storage.DDMFormValues;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.configuration.metatype.bnd.util.ConfigurableUtil;
import com.liferay.portal.kernel.exception.ModelListenerException;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.BaseModelListener;
import com.liferay.portal.kernel.model.Contact;
import com.liferay.portal.kernel.model.ModelListener;
import com.liferay.portal.kernel.model.Phone;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.PhoneLocalServiceUtil;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextThreadLocal;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.workflow.WorkflowConstants;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;

@Component(
  immediate = true,
  service = ModelListener.class,
  configurationPid = OnboardingModelListenerConfiguration.PID
)
public class OnboardingModelListener extends BaseModelListener<DDMFormInstanceRecord> {
  
  @Override
  public void onAfterCreate(DDMFormInstanceRecord model) throws ModelListenerException {}
  
  @Override
  public void onAfterUpdate(DDMFormInstanceRecord formRecord) throws ModelListenerException {
    try {
      ServiceContext serviceContext = ServiceContextThreadLocal.getServiceContext();
      
      if (isFormMonitored(formRecord.getFormInstanceId()) && formRecord.getStatus() == 1 && _config.CreateUser()) 
    	  addInactiveUser(formRecord, serviceContext);

      if (isFormMonitored(formRecord.getFormInstanceId()) && formRecord.getStatus() == 0) {
		if (_config.CreateUser()) {
		  activateUser(formRecord);
		  if (_config.AddToRole()) assignRoleToCreatedUser(formRecord);
		}
		if (_config.AddToRole() && !_config.CreateUser()) {
			if (_log.isDebugEnabled()) {
		        _log.debug("Assigning role to user");
			}
		  assignRoleToCreatorUser(formRecord);
		}
      }
    } catch (PortalException e) {
    	_log.error("error at first level" + e.getMessage());
    }
  }
  
  public boolean isFormMonitored(long currentFormID) {
    if(_config.formId().contains(";")) {
      List<String> formsIDs = Arrays.asList(_config.formId().split(";"));
      return formsIDs.contains(Long.toString(currentFormID).trim());
    } else {
      return Long.toString(currentFormID).equals(_config.formId());
    }
  }
  
  public int indexOfMonitoredForm(long currentFormID) {
      List<String> formsIDs = Arrays.asList(_config.formId().split(";"));
      return formsIDs.indexOf(Long.toString(currentFormID));
  }
  
  public long getMonitoredFormRoles(long currentFormID) {
    if(_config.roleID().contains(";")) {
      int indexOfMonitoredForm = indexOfMonitoredForm(currentFormID);
      List<String> formsRoles = Arrays.asList(_config.roleID().split(";"));
      return Long.valueOf(formsRoles.get(indexOfMonitoredForm));
    } else {
      return Long.valueOf(_config.roleID());
    }
  }
  
  public long getMonitoredFormSite(long currentFormID) {
    if(_config.siteID().contains(";")) {
      int indexOfMonitoredForm = indexOfMonitoredForm(currentFormID);
      List<String> formsSites = Arrays.asList(_config.siteID().split(";"));
      return Long.valueOf(formsSites.get(indexOfMonitoredForm));
    } else {
      return Long.valueOf(_config.siteID());
    }
  }
  
  /**
   * Add a user with the status 'inactive' when a form to create an account is submitted for
   * publication.
   * @param formRecord a form submission
   */
  private void addInactiveUser(DDMFormInstanceRecord formRecord, ServiceContext serviceContext) {
    try {
      HashMap<String, ArrayList<String>> FormData = new HashMap<String, ArrayList<String>>();

      DDMFormValues ddmFormValues = _ddmFormInstanceRecordLocalService.getDDMFormValues(formRecord.getDDMFormValues().getDDMForm(), formRecord.getStorageId(), formRecord.getStorageType());
      long currentFormID = formRecord.getFormInstanceId();
      CollectFormValues(ddmFormValues.getDDMFormFieldValuesMap(true), FormData);

      long companyId = formRecord.getCompanyId();
      long userID =_counterLocalService.increment();
      long adminUserId =  _companyLocalService
              .getCompany(companyId)
              .getDefaultUser()
              .getUserId();
      
      String firstName = FormData.get(getFieldID(_config.firstName(),currentFormID)).get(0);
      String middleName = FormData.get(getFieldID(_config.middleName(),currentFormID)) == null ? StringPool.BLANK : FormData.get(getFieldID(_config.middleName(),currentFormID)).get(0);
      String lastName = FormData.get(getFieldID(_config.lastName(),currentFormID)).get(0);
      String emailAddress = FormData.get(getFieldID(_config.emailAddressField(),currentFormID)).get(0);
      DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
      Date dateOfBirth = df.parse(FormData.get(getFieldID(_config.birthDay(), currentFormID)).get(0));
      Calendar calendar = Calendar.getInstance();
      calendar.setTime(dateOfBirth);
      Integer dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
      Integer monthOfYear = calendar.get(Calendar.MONTH);
      Integer year = calendar.get(Calendar.YEAR);
            
      User newUser = _userLocalService.addUser(
    		  adminUserId /* long creatorUserId */, 
    		  companyId /* long companyId */,
    		  false /* boolean autoPassword */,
    		  "liferay" /* String password1 */,
    		  "liferay" /* String password2 */, 
    		  false /* boolean autoScreenName */, 
    		  "User_" + userID /* String screenName */, 
    		  emailAddress /* String emailAddress */,
    		  LocaleUtil.getDefault() /* Locale Locale */, 
    		  firstName /* String firstName */,  
    		  middleName /* String middleName */, 
    		  lastName /* String lastName */, 
    		  0L /* int prefixId */, 
    		  0L /* int suffixId */,
    		  true /* boolean male */, 
    		  monthOfYear /* int birthdayMonth */, 
    		  dayOfMonth /* int birthdayDay */, 
    		  year /* int birthdayYear */, 
    		  StringPool.BLANK /*String jobTitle */, 
			  new long[0] /* long[] groupIds */,
			  new long[0] /* long[] organizationIds */,
			  new long[0] /* long[] roleIds */,
			  new long[0] /* long[] userGroupIds */,
    		  false /* boolean sendEmail */, 
    		  serviceContext /* ServiceContext ServiceContext */);
      Thread.sleep(100);
      newUser.setStatus(WorkflowConstants.STATUS_INACTIVE);
      newUser.setAgreedToTermsOfUse(true);
      newUser.setNew(true);
      
      /* Save phoneNumber */
      String phoneNumber = FormData.get(getFieldID(_config.phoneNumber(),currentFormID)).get(0);
      /**
       *  Phone TypeID:
       *   value="11006" label="Business"
       *   value="11007" label="Business Fax"
       *   value="11008" label="Mobile Phone"
       *   value="11009" label="Other"
       *   value="11011" label="Personal"
       *
       * TODO -> List<ListType> phoneTypes = ListTypeServiceUtil.getListTypes(Contact.class.getName() + ListTypeConstants.PHONE);
       */
            
      Phone phone = PhoneLocalServiceUtil.addPhone(
    		  newUser.getUserId() /* long userID */,
    		  Contact.class.getName() /* String className */, 
    		  newUser.getContactId() /* long ClassPK */,
    		  phoneNumber /* String number */,
    		  StringPool.BLANK /* String extension */, 
    		  11008 /*long typeId */, 
    		  true /* boolean primary */,
    		  serviceContext /* ServiceContext ServiceContext */);
      
      if (_log.isDebugEnabled()) {
    	  _log.debug("Phone Number added: " + phone.getNumber());
      }
            
      _userLocalService.updateUser(newUser);

    } catch (Exception e) {
      _log.error("Error at addInactiveUser Method: " + e.getMessage(), e);
    }
  }
  
  private String getFieldID(String FieldConfigurationValue,long currentFormID) {
    if(FieldConfigurationValue.contains(";"))  {
      int indexOfMonitoredForm = indexOfMonitoredForm(currentFormID);
      List<String> FieldIDs = Arrays.asList(FieldConfigurationValue.split(";"));
      return FieldIDs.get(indexOfMonitoredForm);
    }
    else {
      return FieldConfigurationValue;
    }
  }
  
  /**
   * Update an inactive user with the status 'active' if the form submission has been approved.
   * @param formRecord a form submission
   */
  private void activateUser(DDMFormInstanceRecord formRecord) {
    try {
      HashMap<String, ArrayList<String>> FormData = new HashMap<String, ArrayList<String>>();
      DDMFormValues ddmFormValues = _ddmFormInstanceRecordLocalService.getDDMFormValues(
    		  formRecord.getFormInstance().getDDMForm(), formRecord.getStorageId(), formRecord.getStorageType());
      CollectFormValues(ddmFormValues.getDDMFormFieldValuesMap(true), FormData);
      User user = _userLocalService.getUserByEmailAddress(
        formRecord.getCompanyId(),
          FormData.get(getFieldID(_config.emailAddressField(),formRecord.getFormInstanceId())).get(0)
      );
      user.setStatus(WorkflowConstants.STATUS_APPROVED);
      _userLocalService.updateUser(user);

    } catch (Exception e) {
      _log.error("error at activateUser Method", e);
    }
  }
  
  /*
	Assign user to the selected role in the model listener configurations, this method will 
	be invoked if the user has been created by the Model Listener as a new user.
  */
  private void assignRoleToCreatedUser(DDMFormInstanceRecord formRecord) {
    try {
      long currentFormID = formRecord.getFormInstanceId();
      long RoleID = getMonitoredFormRoles(currentFormID);
      HashMap<String, ArrayList<String>> FormData = new HashMap<String, ArrayList<String>>();
      DDMFormValues ddmFormValues = _ddmFormInstanceRecordLocalService.getDDMFormValues(formRecord.getFormInstance().getDDMForm(), formRecord.getStorageId(), formRecord.getStorageType());
      CollectFormValues(ddmFormValues.getDDMFormFieldValuesMap(false), FormData);
      User user = _userLocalService.getUserByEmailAddress(
        formRecord.getCompanyId(),
        FormData.get(getFieldID(_config.emailAddressField(),formRecord.getFormInstanceId())).get(0)
      );
      _userLocalService.addRoleUser(RoleID, user);
      _userLocalService.addGroupUser(getMonitoredFormSite(formRecord.getFormInstanceId()),user);
    } catch (Exception e) {
      _log.error("error at assignRoleToCreatedUser Method", e);
    }
  }
  
  /*
	Assign user to the selected role in the model listener configurations, this method will 
	be invoked if the user has not been created by the Model Listener and will use the creator
	user to assign the role to.
	 */
  private void assignRoleToCreatorUser(DDMFormInstanceRecord formRecord) {
    try {
      User Creator = _userLocalService.getUserById(formRecord.getUserId());
      long currentFormID = formRecord.getFormInstanceId();
      long RoleID = getMonitoredFormRoles(currentFormID);
      _log.info(RoleID);
      _log.info(
        "assigning role " + _config.roleID() + " to user" + Creator.getUserId()
      );
      _userLocalService.addRoleUser(RoleID, Creator);
      _userLocalService.addGroupUser(getMonitoredFormSite(formRecord.getFormInstanceId()),Creator);
    } catch (Exception e) {
      _log.error("error at assignRoleToCreatorUser Method", e);
    }
  }
  
  private void CollectFormValues(
    Map<String, List<DDMFormFieldValue>> formFieldValueMap,
    HashMap<String, ArrayList<String>> FormData
  ) {
    for (Map.Entry<String, List<DDMFormFieldValue>> entry : formFieldValueMap.entrySet()) {
      for (DDMFormFieldValue formFieldValue : entry.getValue()) {
        try {
          Map<String, List<DDMFormFieldValue>> nested = formFieldValue.getNestedDDMFormFieldValuesMap();
          if (!nested.isEmpty()) {
            CollectFormValues(nested, FormData);
          } else {
            String Value = "Null";
            String FieldName = formFieldValue.getName();
            Value =
              formFieldValue
                .getValue()
                .getString(formFieldValue.getValue().getDefaultLocale());
            if (FormData.get(FieldName) == null) {
              ArrayList<String> data = new ArrayList<String>();
              data.add(Value);
              FormData.put(FieldName, data);
            } else {
              FormData.get(FieldName).add(Value);
            }
          }
        } catch (Exception ex) {
          _log.error("error on "  ,ex);
        }
      }
    }
  }
  
  @Activate
  @Modified
  public void activate(Map<String, String> properties) {
    try {
      _config =
        ConfigurableUtil.createConfigurable(
          OnboardingModelListenerConfiguration.class,
          properties
        );
    } catch (Exception e) {
      _log.error(
        "error while activating Onboarding Model Listener, please provide a valid configurations"
      );
    }
  }
  
  private static final Log _log = LogFactoryUtil.getLog(OnboardingModelListener.class);

  private volatile OnboardingModelListenerConfiguration _config;
  
  @Reference
  private DDMFormInstanceRecordLocalService _ddmFormInstanceRecordLocalService;
  
  @Reference
  private CompanyLocalService _companyLocalService;

  @Reference
  private UserLocalService _userLocalService;
  
  @Reference
  private CounterLocalService _counterLocalService;
}
