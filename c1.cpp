// sample code
#include <iostream>

// Simple functions demonstrating perimeter and area calculations
double rectanglePerimeter(double width, double height) {
    return 2.0 * (width + height);
}

double rectangleArea(double width, double height) {
    return width * height;
}

int main() {
    std::cout << "Sample code: Rectangle perimeter and area\n";
    double w = 0.0, h = 0.0;
    std::cout << "Enter width and height (separated by space): ";
    if (!(std::cin >> w >> h)) {
        std::cerr << "Invalid input. Using default 3.5 x 2.0\n";
        w = 3.5; h = 2.0;
    }
    std::cout << "Rectangle " << w << " x " << h << "\n";
    std::cout << "Perimeter: " << rectanglePerimeter(w,h) << "\n";
    std::cout << "Area: " << rectangleArea(w,h) << "\n";
    return 0;
}
