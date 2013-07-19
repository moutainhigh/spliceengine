AUTOCOMMIT OFF;

-- MODULE  YTS812  

-- SQL Test Suite, V6.0, Interactive SQL, yts812.sql
-- 59-byte ID
-- TEd Version #

-- AUTHORIZATION CTS1              
   set schema CTS1;

--O   SELECT USER FROM HU.ECCO;
  VALUES USER;
-- RERUN if USER value does not match preceding AUTHORIZATION comment
   ROLLBACK WORK;

-- date_time print

-- TEST:7569 <null predicate> with concatenation in <row value constructor>!
-- Added order by
--O   SELECT COUNT (*)
   SELECT *
     FROM TX
     WHERE TX2 || TX3 IS NOT NULL ORDER BY TX1,TX2,TX3;
-- PASS:7569 If COUNT = 3?
-- Added order by
   SELECT TX1 FROM TX
     WHERE TX3 || TX2 IS NULL ORDER BY TX1;
-- PASS:7569 If 2 rows returned in any order?
-- PASS:7569 If TX1 = 1?
-- PASS:7569 If TX1 = 2?

   ROLLBACK WORK;

-- END TEST >>> 7569 <<< END TEST
-- *********************************************
-- *************************************************////END-OF-MODULE
