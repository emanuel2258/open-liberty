CREATE TABLE ASMTestServiceEntity (ID NUMBER(19) NOT NULL, STRDATA VARCHAR(255), PRIMARY KEY (ID));
CREATE TABLE SEQUENCE (SEQ_NAME VARCHAR2(255) NOT NULL, SEQ_COUNT NUMBER);
INSERT INTO SEQUENCE(SEQ_NAME, SEQ_COUNT) values ('SEQ_GEN', 0);
