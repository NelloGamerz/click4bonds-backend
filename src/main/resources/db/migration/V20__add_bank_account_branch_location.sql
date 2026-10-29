-- The branch a bank account is held at, for the DEMAT confirmation letter.
--
-- The letter prints "Your Bank Branch Location" beside the bank name, IFSC and
-- account number. The column is nullable: an account can be recorded before its
-- branch is known, and the letter prints an em dash in that case rather than
-- refusing the deal.
ALTER TABLE bank_accounts ADD COLUMN branch_location VARCHAR(255);
