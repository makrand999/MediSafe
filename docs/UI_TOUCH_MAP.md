# Medac App UI Touch & Input Map

This document maps all interactive buttons, inputs, tabs, and touch targets across the app.
Use this catalog for automated testing and agent interaction so coordinates and element selectors never need to be guessed.

**Device Screen Resolution**: `1080x2392`
**Last Updated**: 2026-09-03 09:05:22

---

## Table of Screens

- [Today Dashboard](#today_dashboard) (12 interactive targets)
- [Add Medication Screen](#add_medication_screen) (18 interactive targets)
- [Calendar Screen](#calendar_screen) (33 interactive targets)
- [History Log Screen](#history_log_screen) (4 interactive targets)
- [Refill Tracker Screen](#refill_tracker_screen) (6 interactive targets)
- [Medicines Screen](#medicines_screen) (17 interactive targets)
- [Reminders Screen](#reminders_screen) (6 interactive targets)
- [Profile Screen](#profile_screen) (12 interactive targets)

---

<a id="today_dashboard"></a>
## Today Dashboard

- **Annotated Screenshot**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/today_dashboard_annotated.png`
- **Data File**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/today_dashboard_map.json`
- **Interactive Elements Count**: 12

### Interactive Elements Table

```
#   | Role       | Label / Text                      | Center (x,y) | Bounds                | Slug                                         
----+------------+-----------------------------------+--------------+-----------------------+----------------------------------------------
#0  | SCROLLVIEW | Medisafe Your medication assis... | (540,1079)   | [55,103][1025,2056]   | scrollview_medisafe_your_medication_assistant
#1  | BUTTON     | Notifications                     | (829,212)    | [763,146][895,278]    | button_notifications                         
#2  | BUTTON     | Profile                           | (967,212)    | [901,146][1033,278]   | button_profile                               
#3  | BUTTON     | Add Medication                    | (289,842)    | [55,694][523,991]     | button_add_medication                        
#4  | BUTTON     | Calendar View                     | (790,842)    | [556,694][1025,991]   | button_calendar_view                         
#5  | BUTTON     | History Log                       | (289,1172)   | [55,1024][523,1321]   | button_history_log                           
#6  | BUTTON     | Refill Tracker                    | (790,1172)   | [556,1024][1025,1321] | button_refill_tracker                        
#7  | BUTTON     | See All                           | (923,1453)   | [822,1387][1025,1519] | button_see_all                               
#8  | BUTTON     | Add your first medication         | (540,1924)   | [224,1858][856,1990]  | button_add_your_first_medication             
#9  | BUTTON     | Medicines                         | (401,2166)   | [275,2056][528,2276]  | button_medicines                             
#10 | BUTTON     | Reminders                         | (677,2166)   | [550,2056][804,2276]  | button_reminders                             
#11 | BUTTON     | Profile                           | (953,2166)   | [826,2056][1080,2276] | button_profile_2                             
```

---

<a id="add_medication_screen"></a>
## Add Medication Screen

- **Annotated Screenshot**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/add_medication_screen_annotated.png`
- **Data File**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/add_medication_screen_map.json`
- **Interactive Elements Count**: 18

### Interactive Elements Table

```
#   | Role       | Label / Text                      | Center (x,y) | Bounds                | Slug                                          
----+------------+-----------------------------------+--------------+-----------------------+-----------------------------------------------
#0  | BUTTON     | Back                              | (99,99)      | [33,33][165,165]      | button_back                                   
#1  | SCROLLVIEW | Scan Prescription or Label Aut... | (540,1099)   | [0,198][1080,2001]    | scrollview_scan_prescription_or_label_auto_fil
#2  | BUTTON     | Scan Prescription or Label Aut... | (540,340)    | [55,253][1025,427]    | button_scan_prescription_or_label_auto_fil    
#3  | INPUT      | e.g., Drug1, Aspirin              | (540,634)    | [55,557][1025,711]    | input_eg_drug1_aspirin                        
#4  | INPUT      | e.g., 100mg, 1 tablet             | (540,918)    | [55,841][1025,995]    | input_eg_100mg_1_tablet                       
#5  | CHECKBOX   | Once daily                        | (203,1202)   | [55,1136][351,1268]   | checkbox_once_daily                           
#6  | CHECKBOX   | Twice daily                       | (526,1202)   | [373,1136][679,1268]  | checkbox_twice_daily                          
#7  | CHECKBOX   | Three times daily                 | (266,1356)   | [55,1290][478,1422]   | checkbox_three_times_daily                    
#8  | CHECKBOX   | Four times daily                  | (700,1356)   | [500,1290][900,1422]  | checkbox_four_times_daily                     
#9  | CHECKBOX   | As needed                         | (202,1510)   | [55,1444][349,1576]   | checkbox_as_needed                            
#10 | BUTTON     | 7                                 | (143,1781)   | [55,1715][232,1847]   | button_7                                      
#11 | BUTTON     | 14                                | (342,1781)   | [254,1715][431,1847]  | button_14                                     
#12 | BUTTON     | 30                                | (541,1781)   | [453,1715][629,1847]  | button_30                                     
#13 | BUTTON     | 90                                | (739,1781)   | [651,1715][827,1847]  | button_90                                     
#14 | BUTTON     | ∞ Ongoing                         | (937,1781)   | [849,1715][1025,1847] | button_ongoing                                
#15 | BUTTON     | Starts 03/09/2026                 | (540,1953)   | [55,1905][1025,2001]  | button_starts_03092026                        
#16 | BUTTON     | Add Medication                    | (540,2116)   | [55,2045][1025,2188]  | button_add_medication                         
#17 | BUTTON     | Cancel                            | (540,2282)   | [55,2216][1025,2348]  | button_cancel                                 
```

---

<a id="calendar_screen"></a>
## Calendar Screen

- **Annotated Screenshot**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/calendar_screen_annotated.png`
- **Data File**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/calendar_screen_map.json`
- **Interactive Elements Count**: 33

### Interactive Elements Table

```
#   | Role   | Label / Text   | Center (x,y) | Bounds              | Slug                 
----+--------+----------------+--------------+---------------------+----------------------
#0  | BUTTON | Back           | (99,99)      | [33,33][165,165]    | button_back          
#1  | BUTTON | Previous Month | (165,330)    | [99,264][231,396]   | button_previous_month
#2  | BUTTON | Next Month     | (915,330)    | [849,264][981,396]  | button_next_month    
#3  | BUTTON | 1              | (412,540)    | [349,483][475,598]  | button_1             
#4  | BUTTON | 2              | (538,540)    | [475,483][601,598]  | button_2             
#5  | BUTTON | 3              | (664,540)    | [601,483][727,598]  | button_3             
#6  | BUTTON | 4              | (790,540)    | [727,483][853,598]  | button_4             
#7  | BUTTON | 5              | (919,540)    | [853,483][985,598]  | button_5             
#8  | BUTTON | 6              | (160,655)    | [97,598][223,713]   | button_6             
#9  | BUTTON | 7              | (286,655)    | [223,598][349,713]  | button_7             
#10 | BUTTON | 8              | (412,655)    | [349,598][475,713]  | button_8             
#11 | BUTTON | 9              | (538,655)    | [475,598][601,713]  | button_9             
#12 | BUTTON | 10             | (664,655)    | [601,598][727,713]  | button_10            
#13 | BUTTON | 11             | (790,655)    | [727,598][853,713]  | button_11            
#14 | BUTTON | 12             | (919,655)    | [853,598][985,713]  | button_12            
#15 | BUTTON | 13             | (160,770)    | [97,713][223,828]   | button_13            
#16 | BUTTON | 14             | (286,770)    | [223,713][349,828]  | button_14            
#17 | BUTTON | 15             | (412,770)    | [349,713][475,828]  | button_15            
#18 | BUTTON | 16             | (538,770)    | [475,713][601,828]  | button_16            
#19 | BUTTON | 17             | (664,770)    | [601,713][727,828]  | button_17            
#20 | BUTTON | 18             | (790,770)    | [727,713][853,828]  | button_18            
#21 | BUTTON | 19             | (919,770)    | [853,713][985,828]  | button_19            
#22 | BUTTON | 20             | (160,885)    | [97,828][223,943]   | button_20            
#23 | BUTTON | 21             | (286,885)    | [223,828][349,943]  | button_21            
#24 | BUTTON | 22             | (412,885)    | [349,828][475,943]  | button_22            
#25 | BUTTON | 23             | (538,885)    | [475,828][601,943]  | button_23            
#26 | BUTTON | 24             | (664,894)    | [601,828][727,960]  | button_24            
#27 | BUTTON | 25             | (790,894)    | [727,828][853,960]  | button_25            
#28 | BUTTON | 26             | (919,894)    | [853,828][985,960]  | button_26            
#29 | BUTTON | 27             | (160,1009)   | [97,943][223,1075]  | button_27            
#30 | BUTTON | 28             | (286,1009)   | [223,943][349,1075] | button_28            
#31 | BUTTON | 29             | (412,1009)   | [349,943][475,1075] | button_29            
#32 | BUTTON | 30             | (541,1009)   | [475,943][607,1075] | button_30            
```

---

<a id="history_log_screen"></a>
## History Log Screen

- **Annotated Screenshot**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/history_log_screen_annotated.png`
- **Data File**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/history_log_screen_map.json`
- **Interactive Elements Count**: 4

### Interactive Elements Table

```
#  | Role   | Label / Text   | Center (x,y) | Bounds              | Slug                 
---+--------+----------------+--------------+---------------------+----------------------
#0 | BUTTON | Back           | (99,99)      | [33,33][165,165]    | button_back          
#1 | BUTTON | Taken          | (540,264)    | [360,198][720,330]  | button_taken         
#2 | BUTTON | Missed         | (900,264)    | [720,198][1080,330] | button_missed        
#3 | BUTTON | Clear All Data | (540,756)    | [55,690][1025,822]  | button_clear_all_data
```

---

<a id="refill_tracker_screen"></a>
## Refill Tracker Screen

- **Annotated Screenshot**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/refill_tracker_screen_annotated.png`
- **Data File**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/refill_tracker_screen_map.json`
- **Interactive Elements Count**: 6

### Interactive Elements Table

```
#  | Role       | Label / Text                      | Center (x,y) | Bounds               | Slug                                          
---+------------+-----------------------------------+--------------+----------------------+-----------------------------------------------
#0 | BUTTON     | Back                              | (99,99)      | [33,33][165,165]     | button_back                                   
#1 | SCROLLVIEW | Medication Supplies PARACIP-50... | (540,1295)   | [55,198][1025,2392]  | scrollview_medication_supplies_paracip_500_1_t
#2 | BUTTON     | Record Refill                     | (540,726)    | [105,660][975,792]   | button_record_refill                          
#3 | BUTTON     | Record Refill                     | (540,1270)   | [105,1204][975,1336] | button_record_refill_2                        
#4 | BUTTON     | Record Refill                     | (540,1814)   | [105,1748][975,1880] | button_record_refill_3                        
#5 | BUTTON     | Record Refill                     | (540,2342)   | [105,2292][975,2392] | button_record_refill_4                        
```

---

<a id="medicines_screen"></a>
## Medicines Screen

- **Annotated Screenshot**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/medicines_screen_annotated.png`
- **Data File**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/medicines_screen_map.json`
- **Interactive Elements Count**: 17

### Interactive Elements Table

```
#   | Role       | Label / Text                      | Center (x,y) | Bounds                | Slug                                          
----+------------+-----------------------------------+--------------+-----------------------+-----------------------------------------------
#0  | BUTTON     | Add                               | (915,322)    | [806,256][1025,388]   | button_add                                    
#1  | INPUT      | Search medicines, doses...        | (540,504)    | [55,427][1025,581]    | input_search_medicines_doses                  
#2  | BUTTON     | All (5)                           | (877,664)    | [729,598][1025,730]   | button_all_5                                  
#3  | SCROLLVIEW | PARACIP-500 Active 1 tablet On... | (540,1401)   | [55,747][1025,2056]   | scrollview_paracip_500_active_1_tablet_once_da
#4  | BUTTON     | PARACIP-500 Active 1 tablet On... | (540,876)    | [55,747][1025,1005]   | button_paracip_500_active_1_tablet_once_da    
#5  | BUTTON     | Upload photo                      | (168,876)    | [99,807][237,945]     | button_upload_photo                           
#6  | BUTTON     | GLITCH-M1 Active 1 tablet Once... | (540,1167)   | [55,1038][1025,1296]  | button_glitch_m1_active_1_tablet_once_dail    
#7  | BUTTON     | Upload photo                      | (168,1167)   | [99,1098][237,1236]   | button_upload_photo_2                         
#8  | BUTTON     | t-MD Active 1 tablet Once dail... | (540,1458)   | [55,1329][1025,1587]  | button_t_md_active_1_tablet_once_daily        
#9  | BUTTON     | Upload photo                      | (168,1458)   | [99,1389][237,1527]   | button_upload_photo_3                         
#10 | BUTTON     | Dolo-650 Active 1 tablet Once ... | (540,1749)   | [55,1620][1025,1878]  | button_dolo_650_active_1_tablet_once_daily    
#11 | BUTTON     | Upload photo                      | (168,1749)   | [99,1680][237,1818]   | button_upload_photo_4                         
#12 | BUTTON     | Weltone Gold+ Active 1 mL · li... | (540,1983)   | [55,1911][1025,2056]  | button_weltone_gold_active_1_ml_liquid        
#13 | BUTTON     | -                                 | (168,2013)   | [99,1971][237,2056]   | button_button                                 
#14 | BUTTON     | Today                             | (126,2166)   | [0,2056][253,2276]    | button_today                                  
#15 | BUTTON     | Reminders                         | (677,2166)   | [550,2056][804,2276]  | button_reminders                              
#16 | BUTTON     | Profile                           | (953,2166)   | [826,2056][1080,2276] | button_profile                                
```

---

<a id="reminders_screen"></a>
## Reminders Screen

- **Annotated Screenshot**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/reminders_screen_annotated.png`
- **Data File**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/reminders_screen_map.json`
- **Interactive Elements Count**: 6

### Interactive Elements Table

```
#  | Role     | Label / Text | Center (x,y) | Bounds                | Slug             
---+----------+--------------+--------------+-----------------------+------------------
#0 | BUTTON   | 22:00        | (153,359)    | [83,293][224,425]     | button_2200      
#1 | BUTTON   | 07:00        | (367,359)    | [297,293][438,425]    | button_0700      
#2 | CHECKBOX | -            | (925,518)    | [854,452][997,584]    | checkbox_checkbox
#3 | BUTTON   | Today        | (126,2166)   | [0,2056][253,2276]    | button_today     
#4 | BUTTON   | Medicines    | (401,2166)   | [275,2056][528,2276]  | button_medicines 
#5 | BUTTON   | Profile      | (953,2166)   | [826,2056][1080,2276] | button_profile   
```

---

<a id="profile_screen"></a>
## Profile Screen

- **Annotated Screenshot**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/profile_screen_annotated.png`
- **Data File**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/profile_screen_map.json`
- **Interactive Elements Count**: 12

### Interactive Elements Table

```
#   | Role       | Label / Text                      | Center (x,y) | Bounds               | Slug                                          
----+------------+-----------------------------------+--------------+----------------------+-----------------------------------------------
#0  | SCROLLVIEW | Profile M makarandshinde8i Act... | (540,1079)   | [0,103][1080,2056]   | scrollview_profile_m_makarandshinde8i_active_5
#1  | BUTTON     | People & sharing Patients, mem... | (540,824)    | [44,747][1036,901]   | button_people_sharing_patients_members_inv    
#2  | BUTTON     | Alert preferences Delays, quie... | (540,979)    | [44,902][1036,1056]  | button_alert_preferences_delays_quiet_hour    
#3  | BUTTON     | Doctor report Adherence & CSV ... | (540,1134)   | [44,1057][1036,1211] | button_doctor_report_adherence_csv_export     
#4  | BUTTON     | Assistant AI conversations ›      | (540,1289)   | [44,1212][1036,1366] | button_assistant_ai_conversations             
#5  | BUTTON     | Symptoms Timeline & correction... | (540,1444)   | [44,1367][1036,1521] | button_symptoms_timeline_corrections          
#6  | BUTTON     | Exports CSV · download · 1h li... | (540,1599)   | [44,1522][1036,1676] | button_exports_csv_download_1h_link           
#7  | BUTTON     | Account & security Password, s... | (540,1877)   | [44,1800][1036,1954] | button_account_security_password_sessions     
#8  | BUTTON     | Security MFA, active sessions ... | (540,2005)   | [44,1955][1036,2056] | button_security_mfa_active_sessions           
#9  | BUTTON     | Today                             | (126,2166)   | [0,2056][253,2276]   | button_today                                  
#10 | BUTTON     | Medicines                         | (401,2166)   | [275,2056][528,2276] | button_medicines                              
#11 | BUTTON     | Reminders                         | (677,2166)   | [550,2056][804,2276] | button_reminders                              
```

---

## Sub-Screens, Profile Options & Deep Input Maps

<a id="profile_alert_preferences"></a>
## Profile - Alert Preferences

- **Annotated Screenshot**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/profile_alert_preferences/screen_annotated.png`
- **Data File**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/profile_alert_preferences/screen_map.json`

### Interactive Elements Table

```
#  | Role   | Label / Text | Center (x,y) | Bounds | Slug
---+--------+--------------+--------------+--------+-----
#0 | BUTTON | low stock Enabled · 0min delay... | (540,516) | [44,422][1036,611] | button_low_stock_enabled_0min_delay_quiet
#1 | CHECKBOX | - | (925,517) | [854,451][997,583] | checkbox_checkbox
#2 | BUTTON | expiration Enabled · 0min dela... | (540,706) | [44,612][1036,801] | button_expiration_enabled_0min_delay_quiet
#3 | CHECKBOX | - | (925,707) | [854,641][997,773] | checkbox_checkbox_2
#4 | BUTTON | missed user attention med Enab... | (540,896) | [44,802][1036,991] | button_missed_user_attention_med_enabled_0
#5 | CHECKBOX | - | (925,897) | [854,831][997,963] | checkbox_checkbox_3
#6 | BUTTON | stale device Enabled · 0min de... | (540,1086) | [44,992][1036,1181] | button_stale_device_enabled_0min_delay_qui
#7 | CHECKBOX | - | (925,1087) | [854,1021][997,1153] | checkbox_checkbox_4
#8 | BUTTON | generic Enabled · 0min delay ·... | (540,1276) | [44,1182][1036,1371] | button_generic_enabled_0min_delay_quiet_22
#9 | CHECKBOX | - | (925,1277) | [854,1211][997,1343] | checkbox_checkbox_5
#10 | BUTTON | Refresh | (286,1466) | [44,1400][529,1532] | button_refresh
#11 | BUTTON | Save | (793,1466) | [551,1400][1036,1532] | button_save
#12 | BUTTON | Generate now | (540,1722) | [83,1656][997,1788] | button_generate_now
#13 | BUTTON | Back | (77,191) | [11,125][143,257] | button_back
```

---

<a id="profile_doctor_report"></a>
## Profile - Doctor Report

- **Annotated Screenshot**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/profile_doctor_report/screen_annotated.png`
- **Data File**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/profile_doctor_report/screen_map.json`

### Interactive Elements Table

```
#  | Role   | Label / Text | Center (x,y) | Bounds | Slug
---+--------+--------------+--------------+--------+-----
#0 | BUTTON | 2026-08-04 | (198,596) | [83,530][313,662] | button_2026_08_04
#1 | BUTTON | 2026-09-04 | (506,596) | [391,530][621,662] | button_2026_09_04
#2 | BUTTON | Load | (898,595) | [799,529][997,661] | button_load
#3 | BUTTON | Generate alerts | (540,1357) | [83,1291][997,1423] | button_generate_alerts
#4 | BUTTON | Back | (77,191) | [11,125][143,257] | button_back
```

---

<a id="profile_assistant_ai_chat_&_draft"></a>
## Profile - Assistant (AI Chat & Draft)

- **Annotated Screenshot**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/profile_assistant/screen_annotated.png`
- **Data File**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/profile_assistant/screen_map.json`

### Interactive Elements Table

```
#  | Role   | Label / Text | Center (x,y) | Bounds | Slug
---+--------+--------------+--------------+--------+-----
#0 | BUTTON | Chat | (213,357) | [55,291][371,423] | button_chat
#1 | BUTTON | Draft | (540,357) | [382,291][698,423] | button_draft
#2 | BUTTON | Summary | (867,357) | [709,291][1025,423] | button_summary
#3 | INPUT | e.g. Take 1 tablet twice daily | (540,1000) | [77,923][1003,1077] | input_eg_take_1_tablet_twice_daily
#4 | BUTTON | Parse | (540,1166) | [77,1100][1003,1232] | button_parse
#5 | BUTTON | Delete conversation | (211,1363) | [44,1297][378,1429] | button_delete_conversation
#6 | BUTTON | Adherence last 7d? | (201,2033) | [44,1972][358,2094] | button_adherence_last_7d
#7 | BUTTON | What times should I take? | (580,2038) | [380,1972][781,2104] | button_what_times_should_i_take
#8 | INPUT | Ask assistant… | (409,2171) | [44,2094][775,2248] | input_ask_assistant
#9 | BUTTON | Send | (919,2171) | [803,2105][1036,2237] | button_send
#10 | BUTTON | Back | (77,191) | [11,125][143,257] | button_back
```

---

<a id="profile_symptoms_timeline"></a>
## Profile - Symptoms Timeline

- **Annotated Screenshot**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/profile_symptoms/screen_annotated.png`
- **Data File**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/profile_symptoms/screen_map.json`

### Interactive Elements Table

```
#  | Role   | Label / Text | Center (x,y) | Bounds | Slug
---+--------+--------------+--------------+--------+-----
#0 | BUTTON | + Add symptom | (540,489) | [44,423][1036,555] | button_add_symptom
#1 | BUTTON | Back | (77,191) | [11,125][143,257] | button_back
```

---

<a id="profile_add_symptom_dialog"></a>
## Profile - Add Symptom Dialog

- **Annotated Screenshot**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/profile_add_symptom/screen_annotated.png`
- **Data File**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/profile_add_symptom/screen_map.json`

### Interactive Elements Table

```
#  | Role   | Label / Text | Center (x,y) | Bounds | Slug
---+--------+--------------+--------------+--------+-----
#0 | BUTTON | 2026-09-03 | (300,928) | [166,862][434,994] | button_2026_09_03
#1 | BUTTON | 09:11 | (538,928) | [456,862][620,994] | button_0911
#2 | INPUT | Note | (540,1116) | [166,1000][914,1232] | input_note
#3 | INPUT | Linked dose event id (optional... | (540,1348) | [166,1260][914,1436] | input_linked_dose_event_id_optional
#4 | BUTTON | Cancel | (562,1568) | [461,1502][663,1634] | button_cancel
#5 | BUTTON | Save | (799,1569) | [685,1503][914,1635] | button_save
```

---

<a id="profile_account_&_security"></a>
## Profile - Account & Security

- **Annotated Screenshot**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/profile_account/screen_annotated.png`
- **Data File**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/profile_account/screen_map.json`

### Interactive Elements Table

```
#  | Role   | Label / Text | Center (x,y) | Bounds | Slug
---+--------+--------------+--------------+--------+-----
#0 | SCROLLVIEW | Account Email makarandshinde8i... | (540,1277) | [0,279][1080,2276] | scrollview_account_email_makarandshinde8i_pati
#1 | INPUT | Display name | (540,832) | [83,744][997,920] | input_display_name
#2 | INPUT | Timezone (IANA e.g. America/Ne... | (540,1036) | [83,948][997,1124] | input_timezone_iana_eg_americanew_york
#3 | BUTTON | private | (151,1248) | [83,1184][220,1313] | button_private
#4 | BUTTON | generic | (314,1248) | [242,1184][387,1313] | button_generic
#5 | BUTTON | detailed | (485,1248) | [409,1184][561,1313] | button_detailed
#6 | BUTTON | Save patient | (540,1379) | [83,1313][997,1445] | button_save_patient
#7 | BUTTON | Archive | (306,1539) | [83,1473][529,1605] | button_archive
#8 | BUTTON | Request deletion | (774,1539) | [551,1473][997,1605] | button_request_deletion
#9 | INPUT | Current password | (540,1939) | [83,1851][997,2027] | input_current_password
#10 | INPUT | New password (≥12 chars) | (540,2143) | [83,2055][997,2231] | input_new_password_12_chars
#11 | BUTTON | - | (540,2301) | [83,2260][997,2342] | button_button
#12 | BUTTON | Back | (77,191) | [11,125][143,257] | button_back
```

---

<a id="profile_people_&_sharing"></a>
## Profile - People & Sharing

- **Annotated Screenshot**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/profile_sharing/screen_annotated.png`
- **Data File**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/profile_sharing/screen_map.json`

### Interactive Elements Table

```
#  | Role   | Label / Text | Center (x,y) | Bounds | Slug
---+--------+--------------+--------------+--------+-----
#0 | SCROLLVIEW | People & sharing Members (1) R... | (540,1277) | [0,279][1080,2276] | scrollview_people_sharing_members_1_refresh_41
#1 | BUTTON | Refresh | (917,532) | [837,466][997,598] | button_refresh
#2 | BUTTON | Change role | (186,914) | [83,848][289,980] | button_change_role
#3 | BUTTON | Revoke | (386,914) | [306,848][466,980] | button_revoke
#4 | INPUT | Email | (540,1235) | [83,1147][997,1323] | input_email
#5 | BUTTON | viewer | (149,1384) | [83,1320][215,1449] | button_viewer
#6 | BUTTON | contributor | (334,1384) | [237,1320][432,1449] | button_contributor
#7 | BUTTON | manager | (535,1384) | [454,1320][617,1449] | button_manager
#8 | BUTTON | owner | (703,1384) | [637,1320][769,1449] | button_owner
#9 | BUTTON | Send invite | (540,1515) | [83,1449][997,1581] | button_send_invite
#10 | BUTTON | Refresh | (917,1818) | [837,1752][997,1884] | button_refresh_2
#11 | INPUT | Invite token | (540,2268) | [83,2195][997,2342] | input_invite_token
#12 | BUTTON | Back | (77,191) | [11,125][143,257] | button_back
```

---

<a id="medicine_detail_view"></a>
## Medicine Detail View

- **Annotated Screenshot**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/medicine_detail_dolo/screen_annotated.png`
- **Data File**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/medicine_detail_dolo/screen_map.json`

### Interactive Elements Table

```
#  | Role   | Label / Text | Center (x,y) | Bounds | Slug
---+--------+--------------+--------------+--------+-----
#0 | BUTTON | Back | (99,202) | [33,136][165,268] | button_back
#1 | BUTTON | Edit Medicine | (981,202) | [915,136][1047,268] | button_edit_medicine
#2 | SCROLLVIEW | Dolo-650 1 tablet Active Purpo... | (540,1288) | [55,301][1025,2276] | scrollview_dolo_650_1_tablet_active_purpose_ta
#3 | BUTTON | Change photo | (187,450) | [110,373][264,527] | button_change_photo
#4 | BUTTON | Take Now | (318,846) | [110,780][526,912] | button_take_now
#5 | BUTTON | Edit | (762,846) | [554,780][970,912] | button_edit
#6 | BUTTON | Pause | (318,994) | [110,928][526,1060] | button_pause
#7 | BUTTON | Archive | (762,994) | [554,928][970,1060] | button_archive
#8 | BUTTON | - | (540,2339) | [105,2336][975,2342] | button_button
```

---

<a id="edit_medicine_dialog"></a>
## Edit Medicine Dialog

- **Annotated Screenshot**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/medicine_edit_dialog/screen_annotated.png`
- **Data File**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/medicine_edit_dialog/screen_map.json`

### Interactive Elements Table

```
#  | Role   | Label / Text | Center (x,y) | Bounds | Slug
---+--------+--------------+--------------+--------+-----
#0 | BUTTON | Close | (956,220) | [890,154][1022,286] | button_close
#1 | SCROLLVIEW | Medication Name Dolo-650 Dosag... | (540,1161) | [32,301][1048,2022] | scrollview_medication_name_dolo_650_dosage_str
#2 | INPUT | Dolo-650 | (540,491) | [87,414][993,568] | input_dolo_650
#3 | INPUT | 1 tablet | (540,764) | [87,687][993,841] | input_1_tablet
#4 | CHECKBOX | Tablet | (193,1031) | [87,965][299,1097] | checkbox_tablet
#5 | CHECKBOX | Capsule | (445,1031) | [321,965][569,1097] | checkbox_capsule
#6 | CHECKBOX | Liquid | (695,1031) | [591,965][799,1097] | checkbox_liquid
#7 | CHECKBOX | Injection | (216,1185) | [87,1119][346,1251] | checkbox_injection
#8 | CHECKBOX | Inhaler | (480,1185) | [368,1119][592,1251] | checkbox_inhaler
#9 | CHECKBOX | Drops | (717,1185) | [614,1119][820,1251] | checkbox_drops
#10 | CHECKBOX | Topical | (203,1339) | [87,1273][319,1405] | checkbox_topical
#11 | CHECKBOX | Other | (439,1339) | [341,1273][538,1405] | checkbox_other
#12 | CHECKBOX | Once daily | (234,1595) | [87,1529][381,1661] | checkbox_once_daily
#13 | CHECKBOX | Twice daily | (557,1595) | [403,1529][712,1661] | checkbox_twice_daily
#14 | CHECKBOX | Three times daily | (301,1749) | [87,1683][516,1815] | checkbox_three_times_daily
#15 | CHECKBOX | Four times daily | (739,1749) | [538,1683][941,1815] | checkbox_four_times_daily
#16 | CHECKBOX | As needed | (236,1903) | [87,1837][385,1969] | checkbox_as_needed
#17 | BUTTON | Cancel | (299,2132) | [76,2066][523,2198] | button_cancel
#18 | BUTTON | Save Changes | (780,2132) | [556,2066][1004,2198] | button_save_changes
```

---

<a id="notifications_screen"></a>
## Notifications Screen

- **Annotated Screenshot**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/notifications_screen/screen_annotated.png`
- **Data File**: `file:///home/max/AndroidStudioProjects/medac/docs/touch_catalog/notifications_screen/screen_map.json`

### Interactive Elements Table

```
#  | Role   | Label / Text | Center (x,y) | Bounds | Slug
---+--------+--------------+--------------+--------+-----
#0 | BUTTON | Back | (99,99) | [33,33][165,165] | button_back
```

---
