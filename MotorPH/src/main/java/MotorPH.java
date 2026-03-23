/**
 * PROGRAM DESCRIPTION:
 * This system is a Java program designed to manage and calculate payroll 
 * for MotorPH. It reads data from an Excel file to authenticate users, 
 * display employee profiles, and compute salaries for all available data.
 * PAYROLL RULE APPLIED:
 * Deductions (SSS, PhilHealth, Pag-IBIG) and Withholding Tax are only applied to the 2nd Cutoff of the month. 
 * The 1st Cutoff is released without deductions.
 */

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.File;
import java.io.FileInputStream;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.LinkedHashSet;

public class MotorPH {
    //This is where the program knows where to look for the Excel file.
    private static final String EXCEL_FILE_PATH = "MotorPH_EmployeeData.xlsx";
    private static final DataFormatter FORMATTER = new DataFormatter();
    private static final Scanner scanner = new Scanner(System.in);
    
    // Simple admin credentials
    private static final String ADMIN_ID = "999"; 
    private static final String ADMIN_PASS = "admin123";

    //this is where the program knows which column to look for the basic salary and hourly rate.
    private static final int BASIC_SALARY_COL = 13; 
    private static final int HOURLY_RATE_COL = 18;  

    // This is the main method, it handles user authentication and directs to the appropriate menu based on the user type (admin or employee).
    public static void main(String[] args) {
        while (true) {
            System.out.println("\n--- Welcome to MotorPH Payroll System ---");
            System.out.print("Enter ID Number (or type 'exit' to quit): ");
            String userId = scanner.nextLine().trim();

            if (userId.equalsIgnoreCase("exit")) break;

            // Admin login check
            if (userId.equals(ADMIN_ID)) {
                System.out.print("Enter Admin Password: ");
                String pass = scanner.nextLine();
                if (pass.equals(ADMIN_PASS)) {
                    showAdminMenu();
                } else {
                    System.out.println("Incorrect password. Access denied.");
                }
            } else {
                if (verifyEmployeeExists(userId)) {
                    showEmployeeMenu(userId);
                } else {
                    System.out.println("Employee ID not found.");
                }
            }
        }
    }

    // Displays the admin menu with options to view employee profiles and etc.
    private static void showAdminMenu() {
        while (true) {
            System.out.println("\n--- ADMIN PANEL ---");
            System.out.println("1. View Specific Employee Profile");
            System.out.println("2. Process Payroll for ONE Employee (All Records)");
            System.out.println("3. Process Payroll for ALL Employees (All Records)");
            System.out.println("4. Add New Employee [WIP]");
            System.out.println("5. Edit Payroll Records [WIP]");
            System.out.println("6. Logout");
            System.out.print("Select: ");
            
            // This is the result of the admin's choice, it directs to the appropriate function based on the input.
            String choice = scanner.nextLine();
            if (choice.equals("1")) {
                System.out.print("Enter Employee ID: ");
                findAndDisplayEmployee(scanner.nextLine());
            } else if (choice.equals("2")) {
                System.out.print("Enter Employee ID: ");
                processPayrollLoop(scanner.nextLine());
            } else if (choice.equals("3")) {
                processAllEmployees();
            } else if (choice.equals("4")) {
                System.out.println("\n[SYSTEM MESSAGE] The 'Add Employee' feature is currently a Work In Progress (WIP).");
            } else if (choice.equals("5")) {
                System.out.println("\n[SYSTEM MESSAGE] The 'Edit Payroll' feature is currently a Work In Progress (WIP).");
            } else if (choice.equals("6")) {
                System.out.println("Logging out...");
                break;
            } else {
                System.out.println("Invalid choice. Please select a valid number.");
            }
        }
    }
    // Emplyoee menu that allows the employee to view their profile and payroll records, or logout.
    private static void showEmployeeMenu(String id) {
        while (true) {
            System.out.println("\n--- EMPLOYEE SELF-SERVICE ---");
            System.out.println("1. View My Profile");
            System.out.println("2. View My Payroll (All Records)");
            System.out.println("3. Logout");
            System.out.print("Select: ");

            String choice = scanner.nextLine();
            if (choice.equals("1")) findAndDisplayEmployee(id);
            else if (choice.equals("2")) processPayrollLoop(id);
            else if (choice.equals("3")) break;
        }
    }
    
    // This method processes the payroll for a specific employee.
    private static void processPayrollLoop(String id) {
        try (FileInputStream fis = new FileInputStream(new File(EXCEL_FILE_PATH));
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            Sheet empSheet = workbook.getSheet("Employee Details");
            Sheet attSheet = workbook.getSheet("Attendance Record");
            Row row = findEmployeeRow(empSheet, id);

            if (row == null) {
                System.out.println("Employee ID not found.");
                return;
            }

            // Dynamically find the hourly rate column index based on the header.
            int hourlyColIndex = HOURLY_RATE_COL;
            Row headerRow = empSheet.getRow(0);
            if (headerRow != null) {
                for (Cell cell : headerRow) {
                    if (getCellValueAsString(cell).toLowerCase().contains("hourly")) {
                        hourlyColIndex = cell.getColumnIndex();
                        break;
                    }
                }
            }

            // This is where the program calculates the payroll for the employee.
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<String> activePeriods = getUniquePeriods(attSheet, id);
            
            String name = getCellValueAsString(row.getCell(2)) + " " + getCellValueAsString(row.getCell(1));
            System.out.println("\nPayroll for: [" + id + "] " + name);
            
            if (activePeriods.isEmpty()) {
                System.out.println("No attendance records found for this employee.");
                return;
            }

            // printing the payroll table header
            System.out.println("-------------------------------------------------------------------------------------------------------------------------");
            System.out.printf("%-8s | %-10s | %-10s | %-10s | %-10s | %-9s | %-10s | %-13s\n", 
                              "Period", "1st Gross", "1st NET", "2nd Gross", "Gov Deduct", "Tax", "2nd NET", "TOTAL NET PAY");
            System.out.println("-------------------------------------------------------------------------------------------------------------------------");

            // Loop through each unique period and calculate the payroll details for both cutoffs, applying deductions only to the 2nd cutoff.
            for (String period : activePeriods) {
                double h1 = calculateHours(attSheet, id, period, 1, 15);
                double h2 = calculateHours(attSheet, id, period, 16, 31);
                double totalHours = h1 + h2;

                if (totalHours > 0) {
                    double hourlyRate = getNumericSafe(row.getCell(hourlyColIndex), evaluator); 
                    if (hourlyRate > 1000) hourlyRate = hourlyRate / 160;

                    double basicSalary = getNumericSafe(row.getCell(BASIC_SALARY_COL), evaluator);
                    if (basicSalary <= 0) basicSalary = hourlyRate * 160;

                    // Calculate gross pay for both cutoffs
                    double gross1 = hourlyRate * h1;
                    double gross2 = hourlyRate * h2;
                    double totalGross = gross1 + gross2;
                    
                    // This is the SSS, PhilHealth, Pag-IBIG, and tax calculations based on the basic salary and total gross pay, applied only to the 2nd cutoff.
                    double sss = calculateSSS(basicSalary);
                    double philHealth = basicSalary * 0.025; 
                    if (philHealth > 2500.00) philHealth = 2500.00; 
                    double pagIbig = 200.00; 
                    
                    double totalGovtDeductions = sss + philHealth + pagIbig;
                    double taxableIncome = totalGross - totalGovtDeductions;
                    double tax = calculateTax(taxableIncome);
                    
                    // RUBRIC FIX: Apply deductions strictly to 2nd cutoff
                    double net1 = gross1; 
                    double net2 = gross2 - (totalGovtDeductions + tax);
                    double totalNetPay = net1 + net2;

                    //  This is where the program prints the payroll details.
                    System.out.printf("%-8s | %,10.2f | %,10.2f | %,10.2f | %,10.2f | %,9.2f | %,10.2f | %,13.2f\n", 
                                      period, gross1, net1, gross2, totalGovtDeductions, tax, net2, totalNetPay);
                }
            }
            System.out.println("-------------------------------------------------------------------------------------------------------------------------");

        } catch (Exception e) {
            System.out.println("Error processing payroll records: " + e.getMessage());
        }
    }

    // This method processes the payroll for all employees by seraching through the employee details sheet and printing the payroll details for each employee found in the attendance records.
    private static void processAllEmployees() {
        try (FileInputStream fis = new FileInputStream(new File(EXCEL_FILE_PATH));
             Workbook workbook = new XSSFWorkbook(fis)) {
            Sheet sheet = workbook.getSheet("Employee Details");
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;
                String id = getCellValueAsString(row.getCell(0));
                processPayrollLoop(id);
            }
        } catch (Exception e) {
            System.out.println("Error reading records.");
        }
    }

    // This method retrieves unique periods from the attendance records for a specific employee.
    private static List<String> getUniquePeriods(Sheet sheet, String id) {
        Set<String> periods = new LinkedHashSet<>();
        for (Row row : sheet) {
            if (getCellValueAsString(row.getCell(0)).equals(id)) {
                String date = getCellValueAsString(row.getCell(3));
                try {
                    String[] parts = date.split("/");
                    if (parts.length >= 3) {
                        String mm = parts[0].length() == 1 ? "0" + parts[0] : parts[0];
                        String yyyy = parts[2].split(" ")[0]; 
                        periods.add(mm + "/" + yyyy);
                    }
                } catch (Exception e) {}
            }
        }
        return new ArrayList<>(periods);
    }

    // maths for calculating the total hours worked by an employee in a given period, considering the shift timings and applying the grace period for late arrivals.
    private static double calculateHours(Sheet sheet, String id, String targetPeriod, int s, int e) {
        double total = 0;
        LocalTime shiftStart = LocalTime.of(8, 0);
        LocalTime shiftEnd = LocalTime.of(17, 0);
        LocalTime grace = LocalTime.of(8, 10);

        for (Row row : sheet) {
            if (getCellValueAsString(row.getCell(0)).equals(id)) {
                String date = getCellValueAsString(row.getCell(3));
                try {
                    String[] parts = date.split("/");
                    if (parts.length >= 3) {
                        String mm = parts[0].length() == 1 ? "0" + parts[0] : parts[0];
                        String yyyy = parts[2].split(" ")[0];
                        
                        if ((mm + "/" + yyyy).equals(targetPeriod)) {
                            int day = Integer.parseInt(parts[1]);
                            if (day >= s && day <= e) {
                                LocalTime in = parseTime(getCellValueAsString(row.getCell(4)));
                                LocalTime out = parseTime(getCellValueAsString(row.getCell(5)));
                                if (in.isBefore(shiftStart) || (in.isAfter(shiftStart) && in.isBefore(grace))) in = shiftStart;
                                if (out.isAfter(shiftEnd)) out = shiftEnd;
                                if (out.isAfter(in)) {
                                    double dur = Duration.between(in, out).toMinutes() / 60.0;
                                    if (dur > 5) dur -= 1.0; 
                                    total += dur;
                                }
                            }
                        }
                    }
                } catch (Exception ex) {}
            }
        }
        return total;
    }

    // This is the math behind the SSS, PhilHealth, Pag-IBIG, and tax calculations based on the basic salary and total gross pay.
    private static double getNumericSafe(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) return 0.0;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return cell.getNumericCellValue();
            } else if (cell.getCellType() == CellType.FORMULA) {
                return evaluator.evaluate(cell).getNumberValue();
            }
            String val = FORMATTER.formatCellValue(cell, evaluator).replace(",", "").replaceAll("[^\\d.]", "");
            return val.isEmpty() ? 0.0 : Double.parseDouble(val);
        } catch (Exception e) { return 0.0; }
    }

    // Maths for SSS calculation based on the salary, based on the given SSS table.
    private static double calculateSSS(double salary) {
        if (salary <= 3250) return 135.00;
        if (salary >= 24750) return 1125.00;
        int steps = (int)((salary - 3250 - 0.01) / 500) + 1;
        return 135.00 + (steps * 22.50);
    }
    
    // withholding tax calculation, the program applies the tax brackets to the taxable income, ensuring that the correct tax amount is calculated based on the employee's earnings after deductions.
    private static double calculateTax(double taxableIncome) {
        if (taxableIncome <= 20833) return 0;
        if (taxableIncome <= 33333) return (taxableIncome - 20833) * 0.15;
        if (taxableIncome <= 66667) return 1875 + (taxableIncome - 33333) * 0.20;
        if (taxableIncome <= 166667) return 8541.67 + (taxableIncome - 66667) * 0.25;
        return 33541.67 + (taxableIncome - 166667) * 0.30;
    }

    
    // This finds and displays the full profile information of an employee based on their ID.
    private static void findAndDisplayEmployee(String searchId) {
        try (FileInputStream fis = new FileInputStream(new File(EXCEL_FILE_PATH));
             Workbook workbook = new XSSFWorkbook(fis)) {
            Sheet sheet = workbook.getSheet("Employee Details");
            Row row = findEmployeeRow(sheet, searchId);
            if (row != null) {
                System.out.println("\n--- Full Profile Information ---");
                System.out.println("ID: " + getCellValueAsString(row.getCell(0)));
                System.out.println("Full Name: " + getCellValueAsString(row.getCell(2)) + " " + getCellValueAsString(row.getCell(1)));
                System.out.println("SSS: " + getCellValueAsString(row.getCell(6)));
                System.out.println("PhilHealth: " + getCellValueAsString(row.getCell(7)));
                System.out.println("TIN: " + getCellValueAsString(row.getCell(8)));
                System.out.println("Status: " + getCellValueAsString(row.getCell(10)));
                System.out.println("Position: " + getCellValueAsString(row.getCell(11)));
                System.out.println("--------------------------------");
            } else {
                System.out.println("Employee not found.");
            }
        } catch (Exception e) {}
    }
    
    // This gets the row of an employee in the Excel sheet based on their ID
    private static Row findEmployeeRow(Sheet sheet, String id) {
        for (Row row : sheet) {
            if (getCellValueAsString(row.getCell(0)).equals(id)) return row;
        }
        return null;
    }

    private static boolean verifyEmployeeExists(String id) {
        try (FileInputStream fis = new FileInputStream(new File(EXCEL_FILE_PATH));
             Workbook workbook = new XSSFWorkbook(fis)) {
            Sheet sheet = workbook.getSheet("Employee Details");
            return findEmployeeRow(sheet, id) != null;
        } catch (Exception e) {}
        return false;
    }

    // Fixes the time parsing to handle both 24-hour and 12-hour formats, ensuring that the program can correctly interpret various time inputs from the attendance records.
    private static LocalTime parseTime(String t) {
        try {
            t = t.trim().toUpperCase();
            if (t.contains("AM") || t.contains("PM")) 
                return LocalTime.parse(t, DateTimeFormatter.ofPattern("h:mm a"));
            return LocalTime.parse(t);
        } catch (Exception e) { return LocalTime.of(8, 0); }
    }

    // Retrieves the cell value as a string, handling nulls and trimming whitespace.
    private static String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        return FORMATTER.formatCellValue(cell).trim();
    }
}
